package com.smartedu.modules.ai_integration.service;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.ai_integration.dto.AiDtos.*;
import com.smartedu.modules.ai_integration.entity.*;
import com.smartedu.modules.ai_integration.repository.*;
import com.smartedu.modules.user_management.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuizService {

    private final QuestionSetRepository questionSetRepository;
    private final QuizTestRepository quizTestRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    @Transactional
    public Map<String, Object> createAndStartQuiz(User user, CreateQuizRequest req) {
        QuestionSet qs = questionSetRepository.findByIdAndUserId(req.getQuestionSetId(), user.getId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy bộ câu hỏi.", HttpStatus.NOT_FOUND));

        if (qs.getQuestions().isEmpty()) {
            throw new BusinessException("Bộ câu hỏi chưa có câu hỏi nào.");
        }

        QuizTest test = QuizTest.builder()
                .user(user)
                .questionSet(qs)
                .title(req.getTitle() != null && !req.getTitle().isBlank() ? req.getTitle() : "Bài kiểm tra: " + qs.getTitle())
                .durationMinutes(req.getDurationMinutes() != null ? req.getDurationMinutes() : 30)
                .totalScore(new BigDecimal("100.00"))
                .testStatus("ACTIVE")
                .build();

        List<Question> questions = new ArrayList<>(qs.getQuestions());
        if (Boolean.TRUE.equals(req.getShuffleQuestions())) {
            Collections.shuffle(questions);
        }

        BigDecimal perQuestionScore = new BigDecimal("100.00")
                .divide(BigDecimal.valueOf(questions.size()), 2, RoundingMode.HALF_UP);

        int order = 1;
        for (Question q : questions) {
            test.getTestQuestions().add(QuizTestQuestion.builder()
                    .quizTest(test)
                    .question(q)
                    .displayOrder(order++)
                    .maxScore(perQuestionScore)
                    .build());
        }
        test = quizTestRepository.save(test);

        QuizAttempt attempt = QuizAttempt.builder()
                .quizTest(test)
                .user(user)
                .startedAt(LocalDateTime.now())
                .attemptStatus("IN_PROGRESS")
                .build();
        attempt = quizAttemptRepository.save(attempt);
 
        List<Map<String, Object>> questionPayload = new ArrayList<>();
        for (QuizTestQuestion qtq : test.getTestQuestions()) {
            List<QuestionOption> opts = new ArrayList<>(qtq.getQuestion().getOptions());
            if (Boolean.TRUE.equals(req.getShuffleOptions())) {
                Collections.shuffle(opts);
            }
            questionPayload.add(Map.of(
                    "quizTestQuestionId", qtq.getId(),
                    "displayOrder", qtq.getDisplayOrder(),
                    "content", qtq.getQuestion().getContent(),
                    "questionType", qtq.getQuestion().getQuestionType(),
                    "options", opts.stream().map(o -> Map.of(
                            "optionId", o.getId(),
                            "label", o.getOptionLabel(),
                            "content", o.getContent()
                    )).toList()
            ));
        }

        return Map.of(
                "sessionId", attempt.getId(),
                "quizTestId", test.getId(),
                "title", test.getTitle(),
                "durationMinutes", test.getDurationMinutes(),
                "startedAt", attempt.getStartedAt(),
                "expiresAt", attempt.getStartedAt().plusMinutes(test.getDurationMinutes()),
                "questions", questionPayload
        );
    }

    @Transactional
    public QuizAttemptResultResponse submitAttempt(User user, Long attemptId, SubmitQuizRequest req) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phiên làm bài.", HttpStatus.NOT_FOUND));

        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền nộp bài phiên này.", HttpStatus.FORBIDDEN);
        }
        if ("SUBMITTED".equals(attempt.getAttemptStatus())) {
            return buildResultResponse(attempt);
        }

        Map<Long, AnswerSubmission> subMap = req.getAnswers() == null ? Map.of() :
                req.getAnswers().stream().collect(Collectors.toMap(
                        AnswerSubmission::getQuizTestQuestionId, a -> a, (a, b) -> b));

        int correct = 0, incorrect = 0, unanswered = 0;
        BigDecimal totalScore100 = BigDecimal.ZERO;

        for (QuizTestQuestion qtq : attempt.getQuizTest().getTestQuestions()) {
            Question q = qtq.getQuestion();
            QuestionOption correctOpt = q.getOptions().stream()
                    .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                    .findFirst().orElse(null);

            AnswerSubmission sub = subMap.get(qtq.getId());
            QuestionOption selectedOpt = null;
            Boolean isCorrect = false;
            BigDecimal awarded = BigDecimal.ZERO;

            if (sub == null || (sub.getSelectedOptionId() == null && (sub.getAnswerText() == null || sub.getAnswerText().isBlank()))) {
                unanswered++;
                isCorrect = false;
            } else {
                if (sub.getSelectedOptionId() != null) {
                    selectedOpt = q.getOptions().stream()
                            .filter(o -> o.getId().equals(sub.getSelectedOptionId()))
                            .findFirst().orElse(null);
                }
                if (selectedOpt != null && Boolean.TRUE.equals(selectedOpt.getIsCorrect())) {
                    correct++;
                    isCorrect = true;
                    awarded = qtq.getMaxScore();
                    totalScore100 = totalScore100.add(awarded);
                } else {
                    incorrect++;
                    isCorrect = false;
                }
            }

            QuizAttemptAnswer ans = QuizAttemptAnswer.builder()
                    .quizAttempt(attempt)
                    .quizTestQuestion(qtq)
                    .selectedOption(selectedOpt)
                    .answerText(sub != null ? sub.getAnswerText() : null)
                    .isCorrect(isCorrect)
                    .awardedScore(awarded)
                    .questionContentSnapshot(q.getContent())
                    .correctAnswerSnapshot(correctOpt != null ? correctOpt.getOptionLabel() + ". " + correctOpt.getContent() : "")
                    .explanationSnapshot(q.getExplanation())
                    .answeredAt(LocalDateTime.now())
                    .build();
            attempt.getAnswers().add(ans);
        }

        LocalDateTime now = LocalDateTime.now();
        attempt.setSubmittedAt(now);
        attempt.setDurationSeconds(Math.max(1, Duration.between(attempt.getStartedAt(), now).getSeconds()));
        attempt.setAttemptStatus("SUBMITTED");
        attempt.setCorrectCount(correct);
        attempt.setIncorrectCount(incorrect);
        attempt.setUnansweredCount(unanswered);
        attempt.setTotalScore(totalScore100.min(new BigDecimal("100.00")));

        attempt = quizAttemptRepository.save(attempt);
        return buildResultResponse(attempt);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> createWrongAnswersReviewSession(User user, Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lượt làm bài.", HttpStatus.NOT_FOUND));

        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Không có quyền truy cập.", HttpStatus.FORBIDDEN);
        }

        List<QuizAttemptAnswer> wrongAnswers = attempt.getAnswers().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsCorrect()))
                .toList();

        return Map.of(
                "attemptId", attempt.getId(),
                "questionSetId", attempt.getQuizTest().getQuestionSet().getId(),
                "totalWrongQuestions", wrongAnswers.size(),
                "items", wrongAnswers.stream().map(wa -> Map.of(
                        "questionId", wa.getQuizTestQuestion().getQuestion().getId(),
                        "questionContent", wa.getQuestionContentSnapshot(),
                        "yourAnswer", wa.getSelectedOption() != null
                                ? wa.getSelectedOption().getOptionLabel() + ". " + wa.getSelectedOption().getContent()
                                : "Bỏ trống",
                        "correctAnswer", wa.getCorrectAnswerSnapshot(),
                        "explanation", Objects.requireNonNullElse(wa.getExplanationSnapshot(), ""),
                        "referenceExcerpt", Objects.requireNonNullElse(wa.getQuizTestQuestion().getQuestion().getReferenceExcerpt(), "")
                )).toList()
        );
    }

    public Map<String, Object> getMyQuizHistory(User user) {
        List<QuizAttempt> attempts = quizAttemptRepository.findByUserIdOrderByStartedAtDesc(user.getId());
        List<QuizAttempt> submitted = attempts.stream()
                .filter(a -> "SUBMITTED".equals(a.getAttemptStatus()) && a.getTotalScore() != null)
                .toList();

        double avgScore = submitted.stream()
                .mapToDouble(a -> a.getTotalScore().doubleValue())
                .average().orElse(0.0);
        double maxScore = submitted.stream()
                .mapToDouble(a -> a.getTotalScore().doubleValue())
                .max().orElse(0.0);

        return Map.of(
                "totalAttempts", submitted.size(),
                "averageScore100", Math.round(avgScore * 100.0) / 100.0,
                "highestScore100", maxScore,
                "history", submitted.stream().map(this::buildResultResponse).toList()
        );
    }

    public QuizAttemptResultResponse getAttemptDetail(User user, Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy bài làm.", HttpStatus.NOT_FOUND));
        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Không có quyền xem bài làm này.", HttpStatus.FORBIDDEN);
        }
        return buildResultResponse(attempt);
    }

    private QuizAttemptResultResponse buildResultResponse(QuizAttempt attempt) {
        BigDecimal s100 = attempt.getTotalScore() != null ? attempt.getTotalScore() : BigDecimal.ZERO;
        BigDecimal s10 = s100.divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);

        List<AttemptAnswerDetail> details = attempt.getAnswers().stream().map(a -> {
            Question q = a.getQuizTestQuestion().getQuestion();
            return AttemptAnswerDetail.builder()
                    .quizTestQuestionId(a.getQuizTestQuestion().getId())
                    .questionId(q.getId())
                    .questionContent(a.getQuestionContentSnapshot())
                    .selectedOptionId(a.getSelectedOption() != null ? a.getSelectedOption().getId() : null)
                    .selectedOptionText(a.getSelectedOption() != null
                            ? a.getSelectedOption().getOptionLabel() + ". " + a.getSelectedOption().getContent()
                            : "Chưa chọn đáp án")
                    .correctAnswerText(a.getCorrectAnswerSnapshot())
                    .isCorrect(a.getIsCorrect())
                    .explanation(a.getExplanationSnapshot())
                    .referenceExcerpt(q.getReferenceExcerpt())
                    .build();
        }).toList();

        return QuizAttemptResultResponse.builder()
                .attemptId(attempt.getId())
                .quizTestId(attempt.getQuizTest().getId())
                .quizTitle(attempt.getQuizTest().getTitle())
                .attemptStatus(attempt.getAttemptStatus())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .durationSeconds(attempt.getDurationSeconds())
                .score100(s100)
                .score10(s10)
                .correctCount(attempt.getCorrectCount())
                .incorrectCount(attempt.getIncorrectCount())
                .unansweredCount(attempt.getUnansweredCount())
                .details(details)
                .build();
    }
}