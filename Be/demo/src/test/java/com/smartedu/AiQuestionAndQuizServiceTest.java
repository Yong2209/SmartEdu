package com.smartedu;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.http.HttpTimeoutException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.ai_integration.dto.AiDtos.CreateQuizRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.CreateReviewSessionRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.GenerateQuestionsRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.GenerationStatusResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.OptionDto;
import com.smartedu.modules.ai_integration.dto.AiDtos.QuestionDto;
import com.smartedu.modules.ai_integration.dto.AiDtos.QuestionSetResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.QuizAttemptResultResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.RegeneratePreviewResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.RegenerateQuestionRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.ReviewAnswerResultResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.ReviewSessionResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.SaveQuestionSetRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.SubmitQuizRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.SubmitReviewAnswerRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.UpdateQuestionRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.WrongQuestionDto;
import com.smartedu.modules.ai_integration.dto.AiDtos.AnswerSubmission;
import com.smartedu.modules.ai_integration.entity.AiCallLog;
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
import com.smartedu.modules.ai_integration.repository.AiCallLogRepository;
import com.smartedu.modules.ai_integration.repository.QuestionRepository;
import com.smartedu.modules.ai_integration.repository.QuestionSetRepository;
import com.smartedu.modules.ai_integration.repository.QuizAttemptAnswerRepository;
import com.smartedu.modules.ai_integration.repository.QuizAttemptRepository;
import com.smartedu.modules.ai_integration.repository.QuizTestRepository;
import com.smartedu.modules.ai_integration.repository.ReviewAttemptRepository;
import com.smartedu.modules.ai_integration.repository.ReviewSessionItemRepository;
import com.smartedu.modules.ai_integration.repository.ReviewSessionRepository;
import com.smartedu.modules.ai_integration.service.AiQuestionService;
import com.smartedu.modules.ai_integration.service.QuizService;
import com.smartedu.modules.learning.entity.LearningMaterial;
import com.smartedu.modules.learning.entity.MaterialContent;
import com.smartedu.modules.learning.entity.QuestionGenerationRequest;
import com.smartedu.modules.learning.repository.LearningMaterialRepository;
import com.smartedu.modules.learning.repository.MaterialContentRepository;
import com.smartedu.modules.learning.repository.QuestionGenerationRequestRepository;
import com.smartedu.modules.user_management.entity.User;

@ExtendWith(MockitoExtension.class)
class AiQuestionAndQuizServiceTest {

    @Mock private LearningMaterialRepository materialRepository;
    @Mock private MaterialContentRepository contentRepository;
    @Mock private QuestionGenerationRequestRepository genRequestRepository;
    @Mock private QuestionSetRepository questionSetRepository;
    @Mock private QuestionRepository questionRepository;
    @Mock private AiCallLogRepository aiCallLogRepository;
    @Mock private QuizTestRepository quizTestRepository;
    @Mock private QuizAttemptRepository quizAttemptRepository;
    @Mock private QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    @Mock private ReviewSessionRepository reviewSessionRepository;
    @Mock private ReviewSessionItemRepository reviewSessionItemRepository;
    @Mock private ReviewAttemptRepository reviewAttemptRepository;

    private StubAiQuestionService aiService;
    private QuizService quizService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private User owner;
    private User otherUser;
    private LearningMaterial material;
    private MaterialContent content;

    static class StubAiQuestionService extends AiQuestionService {
        String nextGeminiResponse;
        Exception nextException;
        String lastPrompt;

        StubAiQuestionService(
                LearningMaterialRepository materialRepository,
                MaterialContentRepository contentRepository,
                QuestionGenerationRequestRepository genRequestRepository,
                QuestionSetRepository questionSetRepository,
                QuestionRepository questionRepository,
                AiCallLogRepository aiCallLogRepository,
                ObjectMapper objectMapper) {
            super(materialRepository, contentRepository, genRequestRepository,
                    questionSetRepository, questionRepository, aiCallLogRepository, objectMapper, null);
        }

        @Override
        protected String invokeGeminiHttp(String prompt) throws Exception {
            this.lastPrompt = prompt;
            if (nextException != null) {
                throw nextException;
            }
            return nextGeminiResponse;
        }
    }

    @BeforeEach
    void setUp() {
        aiService = new StubAiQuestionService(
                materialRepository, contentRepository, genRequestRepository,
                questionSetRepository, questionRepository, aiCallLogRepository, objectMapper);
        ReflectionTestUtils.setField(aiService, "aiApiKey", "test-key");
        ReflectionTestUtils.setField(aiService, "aiModel", "gemini-test");
        ReflectionTestUtils.setField(aiService, "aiProvider", "GEMINI");

        quizService = new QuizService(
                questionSetRepository,
                quizTestRepository,
                quizAttemptRepository,
                quizAttemptAnswerRepository,
                reviewSessionRepository,
                reviewSessionItemRepository,
                reviewAttemptRepository);

        owner = new User();
        owner.setId(1L);
        owner.setUsername("student1");

        otherUser = new User();
        otherUser.setId(2L);
        otherUser.setUsername("student2");

        material = new LearningMaterial();
        material.setId(10L);
        material.setUser(owner);
        material.setTitle("Cơ sở dữ liệu");
        material.setOriginalFileName("csdl.pdf");
        material.setProcessingStatus("COMPLETED");

        content = new MaterialContent();
        content.setId(20L);
        content.setLearningMaterial(material);
        content.setExtractionStatus("COMPLETED");
        content.setExtractedText("Phần 1: Khái niệm cơ sở dữ liệu quan hệ, bảng, khóa chính, khóa ngoại. "
                + "Phần 2: Chuẩn hóa dữ liệu 1NF, 2NF, 3NF và tối ưu truy vấn SQL.");

        lenient().when(aiCallLogRepository.save(any(AiCallLog.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // PB11: Generate MC & TRUE_FALSE, deduplication, partial warning, timeout + retry
    @Test
    void pb11_generateQuestions_supportsTrueFalseAndWarnsOnPartialReturn() {
        when(materialRepository.findById(10L)).thenReturn(Optional.of(material));
        when(contentRepository.findByLearningMaterialId(10L)).thenReturn(Optional.of(content));

        AtomicLong reqIdSeq = new AtomicLong(100);
        when(genRequestRepository.save(any(QuestionGenerationRequest.class))).thenAnswer(inv -> {
            QuestionGenerationRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(reqIdSeq.incrementAndGet());
            return r;
        });
        when(genRequestRepository.findById(anyLong())).thenAnswer(inv -> {
            QuestionGenerationRequest r = new QuestionGenerationRequest();
            r.setId(inv.getArgument(0));
            r.setUser(owner);
            r.setLearningMaterial(material);
            r.setStatus("PROCESSING");
            return Optional.of(r);
        });
        when(materialRepository.getReferenceById(10L)).thenReturn(material);
        when(questionSetRepository.save(any(QuestionSet.class))).thenAnswer(inv -> {
            QuestionSet qs = inv.getArgument(0);
            if (qs.getId() == null) qs.setId(200L);
            return qs;
        });

        // 1 duplicate + 2 unique TRUE_FALSE out of requested 5
        aiService.nextGeminiResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{
                        "text": "[{\\"content\\":\\"Khóa chính cho phép giá trị NULL.\\",\\"explanation\\":\\"Khóa chính phải NOT NULL.\\",\\"difficulty\\":\\"EASY\\",\\"referenceExcerpt\\":\\"khóa chính\\",\\"referencePage\\":1,\\"options\\":[{\\"label\\":\\"A\\",\\"content\\":\\"Đúng\\",\\"isCorrect\\":false},{\\"label\\":\\"B\\",\\"content\\":\\"Sai\\",\\"isCorrect\\":true}]},{\\"content\\":\\"Khóa chính cho phép giá trị NULL.\\",\\"options\\":[{\\"label\\":\\"A\\",\\"content\\":\\"Đúng\\",\\"isCorrect\\":false},{\\"label\\":\\"B\\",\\"content\\":\\"Sai\\",\\"isCorrect\\":true}]},{\\"content\\":\\"Khóa ngoại liên kết hai bảng.\\",\\"options\\":[{\\"label\\":\\"A\\",\\"content\\":\\"True\\",\\"isCorrect\\":true},{\\"label\\":\\"B\\",\\"content\\":\\"False\\",\\"isCorrect\\":false}]}]"
                      }]
                    }
                  }]
                }
                """;

        GenerateQuestionsRequest req = new GenerateQuestionsRequest();
        req.setLearningMaterialId(10L);
        req.setQuestionType("TRUE_FALSE");
        req.setQuestionCount(5);
        req.setDifficulty("EASY");
        req.setContentScope("FIRST_HALF");

        QuestionSetResponse res = aiService.generateQuestions(owner, req);

        assertThat(res.getQuestionCount()).isEqualTo(2);
        assertThat(res.getRequestedQuestionCount()).isEqualTo(5);
        assertThat(res.getGeneratedQuestionCount()).isEqualTo(2);
        assertThat(res.getWarningMessage()).contains("2/5");
        assertThat(res.getQuestions()).hasSize(2);
        assertThat(res.getQuestions().get(0).getOptions()).extracting(OptionDto::getContent)
                .containsExactly("Đúng", "Sai");
        assertThat(res.getQuestions().get(1).getExplanation()).isNull(); // PB12.7: no fabricated explanation
        assertThat(aiService.lastPrompt).contains("TRUE_FALSE");
    }

    @Test
    void pb11_timeoutPersistsFailedStatusAndSupportsRetry() {
        QuestionGenerationRequest persistedReq = new QuestionGenerationRequest();
        persistedReq.setId(101L);
        persistedReq.setUser(owner);
        persistedReq.setLearningMaterial(material);
        persistedReq.setContentScope("FULL_DOCUMENT");
        persistedReq.setQuestionType("MULTIPLE_CHOICE");
        persistedReq.setQuestionCount(1);
        persistedReq.setDifficulty("MEDIUM");
        persistedReq.setStatus("PENDING");

        when(materialRepository.findById(10L)).thenReturn(Optional.of(material));
        when(contentRepository.findByLearningMaterialId(10L)).thenReturn(Optional.of(content));
        when(genRequestRepository.save(any(QuestionGenerationRequest.class))).thenAnswer(inv -> {
            QuestionGenerationRequest r = inv.getArgument(0);
            if (r.getId() == null) r.setId(101L);
            return r;
        });
        when(genRequestRepository.findById(101L)).thenReturn(Optional.of(persistedReq));
        when(genRequestRepository.findByIdAndUserId(101L, 1L)).thenReturn(Optional.of(persistedReq));
        when(materialRepository.getReferenceById(10L)).thenReturn(material);
        when(questionSetRepository.findByGenerationRequestId(101L)).thenReturn(Optional.empty());
        when(questionSetRepository.save(any(QuestionSet.class))).thenAnswer(inv -> {
            QuestionSet qs = inv.getArgument(0);
            qs.setId(205L);
            return qs;
        });

        aiService.nextException = new HttpTimeoutException("read timed out");

        GenerateQuestionsRequest req = new GenerateQuestionsRequest();
        req.setLearningMaterialId(10L);
        req.setQuestionCount(1);
        req.setQuestionType("MULTIPLE_CHOICE");

        assertThatThrownBy(() -> aiService.generateQuestions(owner, req))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).getStatus())
                .isEqualTo(HttpStatus.GATEWAY_TIMEOUT);

        assertThat(persistedReq.getStatus()).isEqualTo("FAILED");
        assertThat(persistedReq.getErrorMessage()).contains("quá thời gian");
        GenerationStatusResponse statusRes = aiService.getGenerationRequestStatus(owner, 101L);
        assertThat(statusRes.getStatus()).isEqualTo("FAILED");

        // Now retry succeeds
        aiService.nextException = null;
        aiService.nextGeminiResponse = """
                {"candidates":[{"content":{"parts":[{"text":"[{\\"content\\":\\"SQL là gì?\\",\\"explanation\\":\\"Ngôn ngữ truy vấn.\\",\\"difficulty\\":\\"MEDIUM\\",\\"options\\":[{\\"label\\":\\"A\\",\\"content\\":\\"Ngôn ngữ truy vấn\\",\\"isCorrect\\":true},{\\"label\\":\\"B\\",\\"content\\":\\"Hệ điều hành\\",\\"isCorrect\\":false}]}]"}]}}]}
                """;
        QuestionSetResponse retried = aiService.retryGenerationRequest(owner, 101L);
        assertThat(retried.getQuestionCount()).isEqualTo(1);
        assertThat(persistedReq.getStatus()).isEqualTo("COMPLETED");
    }

    // PB12 & PB13: View & Edit questions (MC + TRUE_FALSE + ownership)
    @Test
    void pb12_pb13_viewAndEditValidationAndOwnership() {
        QuestionSet qs = buildQuestionSet(owner, 300L, "SAVED", 2);
        Question mcQ = qs.getQuestions().get(0);
        Question tfQ = buildTrueFalseQuestion(qs, 302L, 3);
        qs.getQuestions().add(tfQ);

        when(questionSetRepository.findById(300L)).thenReturn(Optional.of(qs));
        when(questionRepository.findById(mcQ.getId())).thenReturn(Optional.of(mcQ));
        when(questionRepository.findById(tfQ.getId())).thenReturn(Optional.of(tfQ));
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> inv.getArgument(0));

        // Ownership check
        assertThatThrownBy(() -> aiService.getQuestionSetDetail(otherUser, 300L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);

        // Edit MC with 2 correct answers -> fail
        UpdateQuestionRequest invalidMc = new UpdateQuestionRequest();
        invalidMc.setContent("Câu hỏi sửa");
        invalidMc.setOptions(List.of(
                OptionDto.builder().optionLabel("A").content("1").isCorrect(true).build(),
                OptionDto.builder().optionLabel("B").content("2").isCorrect(true).build()
        ));
        assertThatThrownBy(() -> aiService.updateQuestion(owner, mcQ.getId(), invalidMc))
                .isInstanceOf(BusinessException.class);

        // Edit TRUE_FALSE with invalid option content -> fail
        UpdateQuestionRequest invalidTf = new UpdateQuestionRequest();
        invalidTf.setContent("Nhận định Đ/S");
        invalidTf.setOptions(List.of(
                OptionDto.builder().optionLabel("A").content("Có thể").isCorrect(true).build(),
                OptionDto.builder().optionLabel("B").content("Không chắc").isCorrect(false).build()
        ));
        assertThatThrownBy(() -> aiService.updateQuestion(owner, tfQ.getId(), invalidTf))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Đúng hoặc Sai");

        // Valid TRUE_FALSE edit using correctAnswer
        UpdateQuestionRequest validTf = new UpdateQuestionRequest();
        validTf.setContent("Khóa chính có thể trùng lặp.");
        validTf.setExplanation("Khóa chính phải duy nhất.");
        validTf.setCorrectAnswer("Sai");
        QuestionDto updatedTf = aiService.updateQuestion(owner, tfQ.getId(), validTf);
        assertThat(updatedTf.getQuestionStatus()).isEqualTo("MANUALLY_REVIEWED");
        assertThat(updatedTf.getOptions().stream().filter(OptionDto::getIsCorrect).findFirst().get().getContent())
                .isEqualTo("Sai");
    }

    // PB14: Regenerate preview -> reject keeps old; confirm replaces only selected question
    @Test
    void pb14_regeneratePreviewRejectAndConfirm() {
        QuestionSet qs = buildQuestionSet(owner, 400L, "DRAFT", 2);
        qs.setSourceMaterial(material);
        Question q1 = qs.getQuestions().get(0);
        q1.setSourceMaterial(material);
        Question q2 = qs.getQuestions().get(1);
        String originalQ1Content = q1.getContent();
        String originalQ2Content = q2.getContent();

        when(questionRepository.findById(q1.getId())).thenReturn(Optional.of(q1));
        when(contentRepository.findByLearningMaterialId(10L)).thenReturn(Optional.of(content));
        when(questionRepository.findByReplacesQuestionIdAndQuestionStatus(q1.getId(), "PENDING_REPLACEMENT"))
                .thenReturn(List.of());
        when(questionRepository.save(any(Question.class))).thenAnswer(inv -> {
            Question q = inv.getArgument(0);
            if (q.getId() == null) q.setId(999L);
            return q;
        });

        aiService.nextGeminiResponse = """
                {"candidates":[{"content":{"parts":[{"text":"[{\\"content\\":\\"Câu hỏi thay thế mới hoàn toàn\\",\\"explanation\\":\\"Giải thích mới\\",\\"difficulty\\":\\"HARD\\",\\"options\\":[{\\"label\\":\\"A\\",\\"content\\":\\"Đúng 1\\",\\"isCorrect\\":true},{\\"label\\":\\"B\\",\\"content\\":\\"Sai 2\\",\\"isCorrect\\":false}]}]"}]}}]}
                """;

        RegenerateQuestionRequest req = new RegenerateQuestionRequest();
        req.setDifficulty("HARD");
        req.setAdditionalRequirement("Tập trung vào chuẩn hóa");

        RegeneratePreviewResponse previewRes = (RegeneratePreviewResponse) aiService.regenerateQuestion(owner, q1.getId(), req);
        assertThat(previewRes.getPreviewQuestion().getContent()).isEqualTo("Câu hỏi thay thế mới hoàn toàn");
        assertThat(q1.getContent()).isEqualTo(originalQ1Content); // old question NOT overwritten yet

        // Prepare preview entity in repo for reject/confirm
        Question previewEntity = Question.builder()
                .id(999L)
                .questionSet(qs)
                .replacesQuestion(q1)
                .questionType("MULTIPLE_CHOICE")
                .content("Câu hỏi thay thế mới hoàn toàn")
                .explanation("Giải thích mới")
                .difficulty("HARD")
                .displayOrder(-q1.getId().intValue())
                .questionStatus("PENDING_REPLACEMENT")
                .build();
        previewEntity.getOptions().add(QuestionOption.builder()
                .question(previewEntity).optionLabel("A").content("Đúng 1").isCorrect(true).displayOrder(1).build());
        previewEntity.getOptions().add(QuestionOption.builder()
                .question(previewEntity).optionLabel("B").content("Sai 2").isCorrect(false).displayOrder(2).build());

        when(questionRepository.findById(999L)).thenReturn(Optional.of(previewEntity));

        // Reject keeps old
        QuestionDto rejected = aiService.rejectRegenerate(owner, q1.getId(), 999L);
        assertThat(rejected.getContent()).isEqualTo(originalQ1Content);
        verify(questionRepository).delete(previewEntity);

        // Confirm replaces only q1, leaves q2 untouched
        QuestionDto confirmed = aiService.confirmRegenerate(owner, q1.getId(), 999L);
        assertThat(confirmed.getContent()).isEqualTo("Câu hỏi thay thế mới hoàn toàn");
        assertThat(q2.getContent()).isEqualTo(originalQ2Content);
    }

    // PB15, PB16, PB17, PB18, PB19: Full practice quiz + grading + wrong review + history
    @Test
    void pb15_to_pb19_saveStartSubmitReviewAndHistoryFlow() {
        QuestionSet draftSet = buildQuestionSet(owner, 500L, "DRAFT", 3);
        when(questionSetRepository.findById(500L)).thenReturn(Optional.of(draftSet));
        when(questionSetRepository.save(any(QuestionSet.class))).thenAnswer(inv -> inv.getArgument(0));

        // PB16 cannot start quiz on DRAFT set
        CreateQuizRequest startReq = new CreateQuizRequest();
        startReq.setQuestionSetId(500L);
        startReq.setDurationMinutes(30);
        assertThatThrownBy(() -> quizService.createAndStartQuiz(owner, startReq))
                .isInstanceOf(BusinessException.class);

        // PB15 Save set
        SaveQuestionSetRequest saveReq = new SaveQuestionSetRequest();
        saveReq.setTitle("Bộ đề CSDL");
        QuestionSetResponse savedSet = aiService.saveQuestionSet(owner, 500L, saveReq);
        assertThat(savedSet.getSetStatus()).isEqualTo("SAVED");
        assertThat(savedSet.getQuestionCount()).isEqualTo(3);

        // PB16 Start quiz on saved set
        startReq.setShuffleQuestions(false);
        startReq.setShuffleOptions(false);
        when(quizTestRepository.save(any(QuizTest.class))).thenAnswer(inv -> {
            QuizTest t = inv.getArgument(0);
            t.setId(600L);
            long qtqId = 700L;
            for (QuizTestQuestion qtq : t.getTestQuestions()) {
                qtq.setId(qtqId++);
            }
            return t;
        });
        when(quizAttemptRepository.save(any(QuizAttempt.class))).thenAnswer(inv -> {
            QuizAttempt a = inv.getArgument(0);
            if (a.getId() == null) a.setId(800L);
            return a;
        });

        Map<String, Object> started = quizService.createAndStartQuiz(owner, startReq);
        assertThat(started.get("questionCount")).isEqualTo(3);

        // Verify maxScore sums to exact 100.00
        QuizTest test = QuizTest.builder()
                .id(600L).user(owner).questionSet(draftSet).title("Bài kiểm tra: Bộ đề CSDL").durationMinutes(30).build();
        List<QuizTestQuestion> qtqs = new ArrayList<>();
        BigDecimal[] scores = {new BigDecimal("33.34"), new BigDecimal("33.33"), new BigDecimal("33.33")};
        for (int i = 0; i < 3; i++) {
            QuizTestQuestion qtq = QuizTestQuestion.builder()
                    .id(701L + i).quizTest(test).question(draftSet.getQuestions().get(i))
                    .displayOrder(i + 1).maxScore(scores[i]).build();
            qtqs.add(qtq);
        }
        test.setTestQuestions(qtqs);

        QuizAttempt attempt = QuizAttempt.builder()
                .id(800L).quizTest(test).user(owner)
                .startedAt(LocalDateTime.now().minusMinutes(5)).attemptStatus("IN_PROGRESS").build();

        when(quizAttemptRepository.findById(800L)).thenReturn(Optional.of(attempt));

        // PB17 Submit: Q1 correct, Q2 wrong, Q3 unanswered
        SubmitQuizRequest submitReq = new SubmitQuizRequest();
        AnswerSubmission a1 = new AnswerSubmission();
        a1.setQuizTestQuestionId(701L);
        a1.setSelectedOptionId(draftSet.getQuestions().get(0).getOptions().get(0).getId());
        AnswerSubmission a2 = new AnswerSubmission();
        a2.setQuizTestQuestionId(702L);
        a2.setSelectedOptionId(draftSet.getQuestions().get(1).getOptions().get(1).getId()); // wrong
        submitReq.setAnswers(List.of(a1, a2));

        QuizAttemptResultResponse result = quizService.submitAttempt(owner, 800L, submitReq);
        assertThat(result.getCorrectCount()).isEqualTo(1);
        assertThat(result.getIncorrectCount()).isEqualTo(1);
        assertThat(result.getUnansweredCount()).isEqualTo(1);
        assertThat(result.getScore100()).isEqualByComparingTo("33.34");
        assertThat(result.getAttemptStatus()).isEqualTo("SUBMITTED");
        assertThat(result.getTimedOut()).isFalse();

        // Duplicate submit rejected with 409 CONFLICT
        assertThatThrownBy(() -> quizService.submitAttempt(owner, 800L, submitReq))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);

        // PB18 Wrong questions + ReviewSession + retry answer
        List<QuizAttemptAnswer> wrongList = attempt.getAnswers().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getIsCorrect())).toList();
        when(quizAttemptAnswerRepository.findWrongAnswersByUser(1L, null, null, 800L))
                .thenReturn(wrongList);

        List<WrongQuestionDto> wrongs = quizService.getWrongQuestions(owner, null, null, 800L);
        assertThat(wrongs).hasSize(2);
        assertThat(wrongs.get(0).getOptions()).isNotEmpty();

        when(reviewSessionRepository.save(any(ReviewSession.class))).thenAnswer(inv -> {
            ReviewSession rs = inv.getArgument(0);
            if (rs.getId() == null) rs.setId(900L);
            long itemId = 950L;
            for (ReviewSessionItem it : rs.getItems()) {
                if (it.getId() == null) it.setId(itemId++);
            }
            return rs;
        });

        CreateReviewSessionRequest revReq = new CreateReviewSessionRequest();
        revReq.setAttemptId(800L);
        revReq.setQuestionIds(List.of(draftSet.getQuestions().get(1).getId())); // retry 1 wrong question
        ReviewSessionResponse revSession = quizService.createReviewSession(owner, revReq);
        assertThat(revSession.getTotalItems()).isEqualTo(1);

        ReviewSessionItem item = ReviewSessionItem.builder()
                .id(950L)
                .reviewSession(ReviewSession.builder().id(900L).user(owner).items(new ArrayList<>()).build())
                .question(draftSet.getQuestions().get(1))
                .reviewStatus("PENDING")
                .build();
        item.getReviewSession().getItems().add(item);

        when(reviewSessionRepository.findById(900L)).thenReturn(Optional.of(item.getReviewSession()));
        when(reviewSessionItemRepository.findByIdAndReviewSessionId(950L, 900L)).thenReturn(Optional.of(item));
        when(reviewAttemptRepository.save(any(ReviewAttempt.class))).thenAnswer(inv -> inv.getArgument(0));
        when(reviewSessionItemRepository.save(any(ReviewSessionItem.class))).thenAnswer(inv -> inv.getArgument(0));

        SubmitReviewAnswerRequest ansReq = new SubmitReviewAnswerRequest();
        ansReq.setSelectedOptionId(draftSet.getQuestions().get(1).getOptions().get(0).getId()); // correct option
        ReviewAnswerResultResponse revAnsRes = quizService.submitReviewAnswer(owner, 900L, 950L, ansReq);

        assertThat(revAnsRes.getIsCorrect()).isTrue();
        assertThat(revAnsRes.getReviewStatus()).isEqualTo("MASTERED");
        assertThat(revAnsRes.getSessionStatus()).isEqualTo("COMPLETED");
        // Original attempt score untouched!
        assertThat(attempt.getTotalScore()).isEqualByComparingTo("33.34");
        verify(quizAttemptAnswerRepository, never()).save(any());

        // PB19 History sort by score asc/desc
        QuizAttempt attempt2 = QuizAttempt.builder()
                .id(801L).quizTest(test).user(owner)
                .startedAt(LocalDateTime.now().minusMinutes(2))
                .submittedAt(LocalDateTime.now().minusMinutes(1))
                .attemptStatus("SUBMITTED").totalScore(new BigDecimal("100.00"))
                .correctCount(3).incorrectCount(0).unansweredCount(0).build();
        when(quizAttemptRepository.findByUserIdOrderByStartedAtDesc(1L)).thenReturn(List.of(attempt2, attempt));

        Map<String, Object> histAsc = quizService.getMyQuizHistory(owner, "score", "asc");
        @SuppressWarnings("unchecked")
        List<QuizAttemptResultResponse> listAsc = (List<QuizAttemptResultResponse>) histAsc.get("history");
        assertThat(listAsc).extracting(QuizAttemptResultResponse::getScore100)
                .containsExactly(new BigDecimal("33.34"), new BigDecimal("100.00"));
    }

    private QuestionSet buildQuestionSet(User user, Long setId, String status, int count) {
        QuestionSet qs = QuestionSet.builder()
                .id(setId).user(user).title("Test Set").setStatus(status).questions(new ArrayList<>()).build();
        for (int i = 1; i <= count; i++) {
            Question q = Question.builder()
                    .id(setId * 10 + i)
                    .questionSet(qs)
                    .questionType("MULTIPLE_CHOICE")
                    .content("Câu hỏi số " + i)
                    .explanation("Giải thích " + i)
                    .difficulty("MEDIUM")
                    .displayOrder(i)
                    .questionStatus("ACTIVE")
                    .options(new ArrayList<>())
                    .build();
            q.getOptions().add(QuestionOption.builder()
                    .id(q.getId() * 10 + 1).question(q).optionLabel("A").content("Đáp án đúng").isCorrect(true).displayOrder(1).build());
            q.getOptions().add(QuestionOption.builder()
                    .id(q.getId() * 10 + 2).question(q).optionLabel("B").content("Đáp án sai").isCorrect(false).displayOrder(2).build());
            qs.getQuestions().add(q);
        }
        return qs;
    }

    private Question buildTrueFalseQuestion(QuestionSet qs, Long id, int order) {
        Question q = Question.builder()
                .id(id).questionSet(qs).questionType("TRUE_FALSE").content("Nhận định Đ/S")
                .difficulty("EASY").displayOrder(order).questionStatus("ACTIVE").options(new ArrayList<>()).build();
        q.getOptions().add(QuestionOption.builder()
                .id(id * 10 + 1).question(q).optionLabel("A").content("Đúng").isCorrect(true).displayOrder(1).build());
        q.getOptions().add(QuestionOption.builder()
                .id(id * 10 + 2).question(q).optionLabel("B").content("Sai").isCorrect(false).displayOrder(2).build());
        return q;
    }
}
