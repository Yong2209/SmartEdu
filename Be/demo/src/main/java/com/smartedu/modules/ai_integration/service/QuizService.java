package com.smartedu.modules.ai_integration.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.ai_integration.dto.AiDtos.AnswerSubmission;
import com.smartedu.modules.ai_integration.dto.AiDtos.AttemptAnswerDetail;
import com.smartedu.modules.ai_integration.dto.AiDtos.CreateQuizRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.CreateReviewSessionRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.OptionDto;
import com.smartedu.modules.ai_integration.dto.AiDtos.QuizAttemptResultResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.ReviewAnswerResultResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.ReviewItemDto;
import com.smartedu.modules.ai_integration.dto.AiDtos.ReviewSessionResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.SubmitQuizRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.SubmitReviewAnswerRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.WrongQuestionDto;
import com.smartedu.modules.ai_integration.entity.Question;
import com.smartedu.modules.ai_integration.entity.QuestionOption;
import com.smartedu.modules.ai_integration.entity.QuestionSet;
import com.smartedu.modules.ai_integration.entity.QuizAttempt;
import com.smartedu.modules.ai_integration.entity.QuizAttemptAnswer;
import com.smartedu.modules.ai_integration.entity.QuizTest;
import com.smartedu.modules.ai_integration.entity.QuizTestQuestion;
import com.smartedu.modules.ai_integration.entity.ReviewAttempt;
import com.smartedu.modules.ai_integration.entity.ReviewSession;
import com.smartedu.modules.ai_integration.entity.ReviewSessionItem;
import com.smartedu.modules.ai_integration.repository.QuestionSetRepository;
import com.smartedu.modules.ai_integration.repository.QuizAttemptAnswerRepository;
import com.smartedu.modules.ai_integration.repository.QuizAttemptRepository;
import com.smartedu.modules.ai_integration.repository.QuizTestRepository;
import com.smartedu.modules.ai_integration.repository.ReviewAttemptRepository;
import com.smartedu.modules.ai_integration.repository.ReviewSessionItemRepository;
import com.smartedu.modules.ai_integration.repository.ReviewSessionRepository;
import com.smartedu.modules.user_management.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class QuizService {

    private static final int MIN_DURATION_MINUTES = 1;
    private static final int MAX_DURATION_MINUTES = 180;

    private final QuestionSetRepository questionSetRepository;
    private final QuizTestRepository quizTestRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final ReviewSessionRepository reviewSessionRepository;
    private final ReviewSessionItemRepository reviewSessionItemRepository;
    private final ReviewAttemptRepository reviewAttemptRepository;

    @Transactional
    public Map<String, Object> createAndStartQuiz(User user, CreateQuizRequest req) {
        if (req.getDurationMinutes() == null
                || req.getDurationMinutes() < MIN_DURATION_MINUTES
                || req.getDurationMinutes() > MAX_DURATION_MINUTES) {
            throw new BusinessException("Thời gian làm bài phải từ 1 đến 180 phút.");
        }

        QuestionSet qs = questionSetRepository.findById(req.getQuestionSetId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy bộ câu hỏi.", HttpStatus.NOT_FOUND));
        if (!qs.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền tạo bài kiểm tra từ bộ câu hỏi này.", HttpStatus.FORBIDDEN);
        }
        if ("DRAFT".equalsIgnoreCase(qs.getSetStatus())) {
            throw new BusinessException("Vui lòng lưu bộ câu hỏi vào tài khoản trước khi tạo bài kiểm tra.");
        }

        List<Question> questions = qs.getQuestions().stream()
                .filter(q -> !"PENDING_REPLACEMENT".equals(q.getQuestionStatus()))
                .collect(Collectors.toCollection(ArrayList::new));
        if (questions.isEmpty()) {
            throw new BusinessException("Bộ câu hỏi chưa có câu hỏi nào.");
        }

        QuizTest test = QuizTest.builder()
                .user(user)
                .questionSet(qs)
                .title(req.getTitle() != null && !req.getTitle().isBlank()
                        ? req.getTitle().trim()
                        : "Bài kiểm tra: " + qs.getTitle())
                .durationMinutes(req.getDurationMinutes())
                .totalScore(new BigDecimal("100.00"))
                .testStatus("ACTIVE")
                .build();

        if (Boolean.TRUE.equals(req.getShuffleQuestions())) {
            Collections.shuffle(questions);
        }

        BigDecimal baseScore = new BigDecimal("100.00")
                .divide(BigDecimal.valueOf(questions.size()), 2, RoundingMode.DOWN);
        BigDecimal remainder = new BigDecimal("100.00")
                .subtract(baseScore.multiply(BigDecimal.valueOf(questions.size())));

        int order = 1;
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            BigDecimal qScore = (i == 0) ? baseScore.add(remainder) : baseScore;
            test.getTestQuestions().add(QuizTestQuestion.builder()
                    .quizTest(test)
                    .question(q)
                    .displayOrder(order++)
                    .maxScore(qScore)
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
                "questionSetId", qs.getId(),
                "questionSetTitle", qs.getTitle(),
                "questionCount", questions.size(),
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
        if (!"IN_PROGRESS".equalsIgnoreCase(attempt.getAttemptStatus())) {
            throw new BusinessException("Bài làm này đã được nộp trước đó, không thể nộp lại.", HttpStatus.CONFLICT);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = attempt.getStartedAt().plusMinutes(attempt.getQuizTest().getDurationMinutes());
        boolean timedOut = now.isAfter(deadline);

        Map<Long, AnswerSubmission> subMap = (req == null || req.getAnswers() == null) ? Map.of() :
                req.getAnswers().stream()
                        .filter(a -> a.getQuizTestQuestionId() != null)
                        .collect(Collectors.toMap(
                                AnswerSubmission::getQuizTestQuestionId, a -> a, (a, b) -> b));

        int correct = 0;
        int incorrect = 0;
        int unanswered = 0;
        BigDecimal totalScore100 = BigDecimal.ZERO;

        for (QuizTestQuestion qtq : attempt.getQuizTest().getTestQuestions()) {
            Question q = qtq.getQuestion();
            QuestionOption correctOpt = findCorrectOption(q);

            AnswerSubmission sub = subMap.get(qtq.getId());
            QuestionOption selectedOpt = null;
            boolean isCorrect = false;
            BigDecimal awarded = BigDecimal.ZERO;

            boolean noAnswer = sub == null || (sub.getSelectedOptionId() == null
                    && (sub.getAnswerText() == null || sub.getAnswerText().isBlank()));
            if (noAnswer) {
                unanswered++;
            } else {
                selectedOpt = resolveSelectedOption(q, sub.getSelectedOptionId(), sub.getAnswerText());
                if (selectedOpt != null && Boolean.TRUE.equals(selectedOpt.getIsCorrect())) {
                    correct++;
                    isCorrect = true;
                    awarded = qtq.getMaxScore();
                    totalScore100 = totalScore100.add(awarded);
                } else {
                    incorrect++;
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
                    .correctAnswerSnapshot(formatOptionText(correctOpt))
                    .explanationSnapshot(q.getExplanation())
                    .answeredAt(now)
                    .build();
            attempt.getAnswers().add(ans);
        }

        long maxSeconds = attempt.getQuizTest().getDurationMinutes() * 60L;
        long elapsedSeconds = Math.max(1, Duration.between(attempt.getStartedAt(), now).getSeconds());
        attempt.setSubmittedAt(timedOut ? deadline : now);
        attempt.setDurationSeconds(timedOut ? maxSeconds : elapsedSeconds);
        attempt.setAttemptStatus("SUBMITTED");
        attempt.setCorrectCount(correct);
        attempt.setIncorrectCount(incorrect);
        attempt.setUnansweredCount(unanswered);
        attempt.setTotalScore(totalScore100.min(new BigDecimal("100.00")));

        attempt = quizAttemptRepository.save(attempt);
        return buildResultResponse(attempt, timedOut);
    }

    /**
     * PB18: Keeps compatibility with existing POST /api/ai/quizzes/attempts/{attemptId}/review-wrong
     * while also persisting a ReviewSession and returning full options for retrying.
     */
    @Transactional
    public Map<String, Object> createWrongAnswersReviewSession(User user, Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lượt làm bài.", HttpStatus.NOT_FOUND));

        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Không có quyền truy cập.", HttpStatus.FORBIDDEN);
        }
        if (!"SUBMITTED".equalsIgnoreCase(attempt.getAttemptStatus())) {
            throw new BusinessException("Bài kiểm tra chưa được nộp.");
        }

        List<QuizAttemptAnswer> wrongAnswers = attempt.getAnswers().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsCorrect()))
                .sorted(Comparator.comparingInt(a -> a.getQuizTestQuestion().getDisplayOrder()))
                .toList();

        ReviewSession session = null;
        if (!wrongAnswers.isEmpty()) {
            session = persistReviewSession(
                    user,
                    attempt,
                    attempt.getQuizTest().getQuestionSet(),
                    wrongAnswers);
        }

        List<Map<String, Object>> itemsPayload = new ArrayList<>();
        for (int i = 0; i < wrongAnswers.size(); i++) {
            QuizAttemptAnswer wa = wrongAnswers.get(i);
            Question q = wa.getQuizTestQuestion().getQuestion();
            Long itemId = session != null && i < session.getItems().size()
                    ? session.getItems().get(i).getId()
                    : null;

            Map<String, Object> itemMap = new LinkedHashMap<>();
            itemMap.put("reviewSessionItemId", itemId);
            itemMap.put("questionId", q.getId());
            itemMap.put("questionType", q.getQuestionType());
            itemMap.put("questionContent", wa.getQuestionContentSnapshot());
            itemMap.put("yourAnswer", wa.getSelectedOption() != null
                    ? formatOptionText(wa.getSelectedOption())
                    : (wa.getAnswerText() != null && !wa.getAnswerText().isBlank() ? wa.getAnswerText() : "Bỏ trống"));
            itemMap.put("correctAnswer", wa.getCorrectAnswerSnapshot());
            itemMap.put("explanation", Objects.requireNonNullElse(wa.getExplanationSnapshot(), ""));
            itemMap.put("referenceExcerpt", Objects.requireNonNullElse(q.getReferenceExcerpt(), ""));
            itemMap.put("options", toOptionDtos(q));
            itemsPayload.add(itemMap);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("reviewSessionId", session != null ? session.getId() : null);
        res.put("attemptId", attempt.getId());
        res.put("questionSetId", attempt.getQuizTest().getQuestionSet().getId());
        res.put("totalWrongQuestions", wrongAnswers.size());
        res.put("items", itemsPayload);
        return res;
    }

    @Transactional(readOnly = true)
    public List<WrongQuestionDto> getWrongQuestions(User user, Long quizTestId, Long questionSetId, Long attemptId) {
        List<QuizAttemptAnswer> wrongAnswers = quizAttemptAnswerRepository.findWrongAnswersByUser(
                user.getId(), quizTestId, questionSetId, attemptId);

        return wrongAnswers.stream().map(wa -> {
            Question q = wa.getQuizTestQuestion().getQuestion();
            QuizAttempt att = wa.getQuizAttempt();
            QuizTest qt = att.getQuizTest();
            QuestionSet qs = qt.getQuestionSet();
            return WrongQuestionDto.builder()
                    .sourceAttemptAnswerId(wa.getId())
                    .attemptId(att.getId())
                    .quizTestId(qt.getId())
                    .quizTitle(qt.getTitle())
                    .questionSetId(qs.getId())
                    .questionSetTitle(qs.getTitle())
                    .questionId(q.getId())
                    .questionType(q.getQuestionType())
                    .questionContent(wa.getQuestionContentSnapshot())
                    .yourAnswer(wa.getSelectedOption() != null
                            ? formatOptionText(wa.getSelectedOption())
                            : (wa.getAnswerText() != null && !wa.getAnswerText().isBlank() ? wa.getAnswerText() : "Bỏ trống"))
                    .correctAnswer(wa.getCorrectAnswerSnapshot())
                    .explanation(wa.getExplanationSnapshot())
                    .referenceExcerpt(q.getReferenceExcerpt())
                    .submittedAt(att.getSubmittedAt())
                    .options(toOptionDtos(q))
                    .build();
        }).toList();
    }

    @Transactional
    public ReviewSessionResponse createReviewSession(User user, CreateReviewSessionRequest req) {
        CreateReviewSessionRequest effective = req != null ? req : new CreateReviewSessionRequest();

        if (effective.getAttemptId() != null) {
            QuizAttempt attempt = quizAttemptRepository.findById(effective.getAttemptId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy lượt làm bài.", HttpStatus.NOT_FOUND));
            if (!attempt.getUser().getId().equals(user.getId())) {
                throw new BusinessException("Bạn không có quyền truy cập lượt làm bài này.", HttpStatus.FORBIDDEN);
            }
        }

        List<QuizAttemptAnswer> wrongAnswers = quizAttemptAnswerRepository.findWrongAnswersByUser(
                user.getId(),
                effective.getQuizTestId(),
                effective.getQuestionSetId(),
                effective.getAttemptId());

        if (effective.getQuestionIds() != null && !effective.getQuestionIds().isEmpty()) {
            wrongAnswers = wrongAnswers.stream()
                    .filter(wa -> effective.getQuestionIds().contains(wa.getQuizTestQuestion().getQuestion().getId()))
                    .toList();
        }

        if (wrongAnswers.isEmpty()) {
            throw new BusinessException("Không có câu trả lời sai nào phù hợp để ôn tập.", HttpStatus.NOT_FOUND);
        }

        // Deduplicate by questionId while keeping most recent wrong attempt
        Map<Long, QuizAttemptAnswer> distinctByQuestion = new LinkedHashMap<>();
        for (QuizAttemptAnswer wa : wrongAnswers) {
            distinctByQuestion.putIfAbsent(wa.getQuizTestQuestion().getQuestion().getId(), wa);
        }
        List<QuizAttemptAnswer> selected = new ArrayList<>(distinctByQuestion.values());

        QuizAttempt sourceAttempt = effective.getAttemptId() != null
                ? selected.get(0).getQuizAttempt()
                : null;
        QuestionSet sourceSet = effective.getQuestionSetId() != null
                ? selected.get(0).getQuizAttempt().getQuizTest().getQuestionSet()
                : (sourceAttempt != null ? sourceAttempt.getQuizTest().getQuestionSet() : null);

        ReviewSession session = persistReviewSession(user, sourceAttempt, sourceSet, selected);
        return toReviewSessionResponse(session);
    }

    @Transactional(readOnly = true)
    public ReviewSessionResponse getReviewSession(User user, Long sessionId) {
        ReviewSession session = reviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phiên ôn tập.", HttpStatus.NOT_FOUND));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền xem phiên ôn tập này.", HttpStatus.FORBIDDEN);
        }
        return toReviewSessionResponse(session);
    }

    @Transactional
    public ReviewAnswerResultResponse submitReviewAnswer(
            User user, Long sessionId, Long itemId, SubmitReviewAnswerRequest req) {
        ReviewSession session = reviewSessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy phiên ôn tập.", HttpStatus.NOT_FOUND));
        if (!session.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền thao tác phiên ôn tập này.", HttpStatus.FORBIDDEN);
        }

        ReviewSessionItem item = reviewSessionItemRepository.findByIdAndReviewSessionId(itemId, sessionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy câu hỏi trong phiên ôn tập.", HttpStatus.NOT_FOUND));

        if (req == null || (req.getSelectedOptionId() == null && (req.getAnswerText() == null || req.getAnswerText().isBlank()))) {
            throw new BusinessException("Vui lòng chọn hoặc nhập câu trả lời.");
        }

        Question q = item.getQuestion();
        QuestionOption selectedOpt = resolveSelectedOption(q, req.getSelectedOptionId(), req.getAnswerText());
        if (req.getSelectedOptionId() != null && selectedOpt == null) {
            throw new BusinessException("Phương án lựa chọn không thuộc câu hỏi này.");
        }

        boolean isCorrect = selectedOpt != null && Boolean.TRUE.equals(selectedOpt.getIsCorrect());
        LocalDateTime now = LocalDateTime.now();

        reviewAttemptRepository.save(ReviewAttempt.builder()
                .reviewSessionItem(item)
                .selectedOption(selectedOpt)
                .answerText(req.getAnswerText())
                .isCorrect(isCorrect)
                .attemptedAt(now)
                .build());

        item.setLastIsCorrect(isCorrect);
        item.setLastReviewedAt(now);
        item.setReviewStatus(isCorrect ? "MASTERED" : "NEEDS_REVIEW");
        reviewSessionItemRepository.save(item);

        boolean allMastered = session.getItems().stream()
                .allMatch(i -> "MASTERED".equals(i.getReviewStatus()));
        if (allMastered) {
            session.setSessionStatus("COMPLETED");
            session.setEndedAt(now);
        } else {
            session.setSessionStatus("IN_PROGRESS");
            session.setEndedAt(null);
        }
        reviewSessionRepository.save(session);

        QuestionOption correctOpt = findCorrectOption(q);
        return ReviewAnswerResultResponse.builder()
                .reviewSessionId(session.getId())
                .reviewSessionItemId(item.getId())
                .questionId(q.getId())
                .isCorrect(isCorrect)
                .reviewStatus(item.getReviewStatus())
                .sessionStatus(session.getSessionStatus())
                .selectedAnswerText(selectedOpt != null ? formatOptionText(selectedOpt) : req.getAnswerText())
                .correctAnswer(formatOptionText(correctOpt))
                .explanation(q.getExplanation())
                .referenceExcerpt(q.getReferenceExcerpt())
                .build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMyQuizHistory(User user, String sortBy, String sortDir) {
        List<QuizAttempt> attempts = quizAttemptRepository.findByUserIdOrderByStartedAtDesc(user.getId());
        List<QuizAttempt> submitted = new ArrayList<>(attempts.stream()
                .filter(a -> "SUBMITTED".equals(a.getAttemptStatus()) && a.getTotalScore() != null)
                .toList());

        boolean asc = "asc".equalsIgnoreCase(sortDir);
        if ("score".equalsIgnoreCase(sortBy)) {
            Comparator<QuizAttempt> cmp = Comparator.comparing(
                    QuizAttempt::getTotalScore, Comparator.nullsLast(BigDecimal::compareTo));
            submitted.sort(asc ? cmp : cmp.reversed());
        } else {
            Comparator<QuizAttempt> cmp = Comparator.comparing(
                    a -> a.getSubmittedAt() != null ? a.getSubmittedAt() : a.getStartedAt(),
                    Comparator.nullsLast(LocalDateTime::compareTo));
            submitted.sort(asc ? cmp : cmp.reversed());
        }

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
                "history", submitted.stream().map(a -> buildResultResponse(a, false)).toList()
        );
    }

    @Transactional(readOnly = true)
    public QuizAttemptResultResponse getAttemptDetail(User user, Long attemptId) {
        QuizAttempt attempt = quizAttemptRepository.findById(attemptId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy bài làm.", HttpStatus.NOT_FOUND));
        if (!attempt.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Không có quyền xem bài làm này.", HttpStatus.FORBIDDEN);
        }
        return buildResultResponse(attempt, false);
    }

    private ReviewSession persistReviewSession(
            User user,
            QuizAttempt sourceAttempt,
            QuestionSet sourceSet,
            List<QuizAttemptAnswer> wrongAnswers) {
        ReviewSession session = ReviewSession.builder()
                .user(user)
                .sourceQuizAttempt(sourceAttempt)
                .sourceQuestionSet(sourceSet)
                .sessionStatus("IN_PROGRESS")
                .startedAt(LocalDateTime.now())
                .build();

        for (QuizAttemptAnswer wa : wrongAnswers) {
            session.getItems().add(ReviewSessionItem.builder()
                    .reviewSession(session)
                    .question(wa.getQuizTestQuestion().getQuestion())
                    .sourceAttemptAnswer(wa)
                    .reviewStatus("PENDING")
                    .build());
        }
        return reviewSessionRepository.save(session);
    }

    private ReviewSessionResponse toReviewSessionResponse(ReviewSession session) {
        List<ReviewItemDto> itemDtos = session.getItems().stream().map(item -> {
            Question q = item.getQuestion();
            QuestionOption correctOpt = findCorrectOption(q);
            QuizAttemptAnswer src = item.getSourceAttemptAnswer();
            String prevAns = src == null ? null : (src.getSelectedOption() != null
                    ? formatOptionText(src.getSelectedOption())
                    : (src.getAnswerText() != null ? src.getAnswerText() : "Bỏ trống"));

            return ReviewItemDto.builder()
                    .reviewSessionItemId(item.getId())
                    .questionId(q.getId())
                    .questionType(q.getQuestionType())
                    .questionContent(q.getContent())
                    .reviewStatus(item.getReviewStatus())
                    .lastIsCorrect(item.getLastIsCorrect())
                    .lastReviewedAt(item.getLastReviewedAt())
                    .yourPreviousAnswer(prevAns)
                    .correctAnswer(formatOptionText(correctOpt))
                    .explanation(q.getExplanation())
                    .referenceExcerpt(q.getReferenceExcerpt())
                    .options(toOptionDtos(q))
                    .build();
        }).toList();

        int mastered = (int) session.getItems().stream()
                .filter(i -> "MASTERED".equals(i.getReviewStatus()))
                .count();

        return ReviewSessionResponse.builder()
                .reviewSessionId(session.getId())
                .sourceAttemptId(session.getSourceQuizAttempt() != null ? session.getSourceQuizAttempt().getId() : null)
                .sourceQuestionSetId(session.getSourceQuestionSet() != null ? session.getSourceQuestionSet().getId() : null)
                .sessionStatus(session.getSessionStatus())
                .startedAt(session.getStartedAt())
                .endedAt(session.getEndedAt())
                .totalItems(itemDtos.size())
                .masteredCount(mastered)
                .items(itemDtos)
                .build();
    }

    private QuestionOption resolveSelectedOption(Question q, Long selectedOptionId, String answerText) {
        if (selectedOptionId != null) {
            return q.getOptions().stream()
                    .filter(o -> o.getId().equals(selectedOptionId))
                    .findFirst()
                    .orElse(null);
        }
        if (answerText != null && !answerText.isBlank()) {
            String normalized = answerText.trim().toLowerCase(Locale.ROOT);
            for (QuestionOption o : q.getOptions()) {
                if (o.getOptionLabel().equalsIgnoreCase(normalized)
                        || o.getContent().trim().equalsIgnoreCase(normalized)) {
                    return o;
                }
            }
            if ("TRUE_FALSE".equalsIgnoreCase(q.getQuestionType())) {
                boolean wantTrue = normalized.equals("true") || normalized.equals("đúng") || normalized.equals("đ") || normalized.equals("t");
                boolean wantFalse = normalized.equals("false") || normalized.equals("sai") || normalized.equals("s") || normalized.equals("f");
                if (wantTrue || wantFalse) {
                    return q.getOptions().stream()
                            .filter(o -> {
                                String c = o.getContent().trim().toLowerCase(Locale.ROOT);
                                return wantTrue
                                        ? (c.equals("đúng") || c.equals("true"))
                                        : (c.equals("sai") || c.equals("false"));
                            })
                            .findFirst()
                            .orElse(null);
                }
            }
        }
        return null;
    }

    private QuestionOption findCorrectOption(Question q) {
        return q.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .findFirst()
                .orElse(null);
    }

    private String formatOptionText(QuestionOption opt) {
        if (opt == null) {
            return "";
        }
        return opt.getOptionLabel() + ". " + opt.getContent();
    }

    private List<OptionDto> toOptionDtos(Question q) {
        return q.getOptions().stream()
                .map(o -> OptionDto.builder()
                        .id(o.getId())
                        .optionLabel(o.getOptionLabel())
                        .content(o.getContent())
                        .isCorrect(o.getIsCorrect())
                        .displayOrder(o.getDisplayOrder())
                        .build())
                .toList();
    }

    private QuizAttemptResultResponse buildResultResponse(QuizAttempt attempt, boolean timedOut) {
        BigDecimal s100 = attempt.getTotalScore() != null ? attempt.getTotalScore() : BigDecimal.ZERO;
        BigDecimal s10 = s100.divide(BigDecimal.TEN, 2, RoundingMode.HALF_UP);

        List<AttemptAnswerDetail> details = attempt.getAnswers().stream()
                .sorted(Comparator.comparingInt(a -> a.getQuizTestQuestion().getDisplayOrder()))
                .map(a -> {
                    Question q = a.getQuizTestQuestion().getQuestion();
                    String selectedText = a.getSelectedOption() != null
                            ? formatOptionText(a.getSelectedOption())
                            : (a.getAnswerText() != null && !a.getAnswerText().isBlank()
                                    ? a.getAnswerText()
                                    : "Chưa chọn đáp án");
                    return AttemptAnswerDetail.builder()
                            .quizTestQuestionId(a.getQuizTestQuestion().getId())
                            .questionId(q.getId())
                            .questionType(q.getQuestionType())
                            .questionContent(a.getQuestionContentSnapshot())
                            .selectedOptionId(a.getSelectedOption() != null ? a.getSelectedOption().getId() : null)
                            .selectedOptionText(selectedText)
                            .correctAnswerText(a.getCorrectAnswerSnapshot())
                            .isCorrect(a.getIsCorrect())
                            .awardedScore(a.getAwardedScore())
                            .maxScore(a.getQuizTestQuestion().getMaxScore())
                            .explanation(a.getExplanationSnapshot())
                            .referenceExcerpt(q.getReferenceExcerpt())
                            .build();
                })
                .toList();

        QuestionSet qs = attempt.getQuizTest().getQuestionSet();
        return QuizAttemptResultResponse.builder()
                .attemptId(attempt.getId())
                .quizTestId(attempt.getQuizTest().getId())
                .questionSetId(qs != null ? qs.getId() : null)
                .questionSetTitle(qs != null ? qs.getTitle() : null)
                .quizTitle(attempt.getQuizTest().getTitle())
                .attemptStatus(attempt.getAttemptStatus())
                .timedOut(timedOut)
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
