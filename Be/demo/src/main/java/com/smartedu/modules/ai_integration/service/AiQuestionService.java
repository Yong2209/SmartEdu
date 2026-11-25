package com.smartedu.modules.ai_integration.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.ai_integration.dto.AiDtos.GenerateQuestionsRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.GenerationStatusResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.OptionDto;
import com.smartedu.modules.ai_integration.dto.AiDtos.QuestionDto;
import com.smartedu.modules.ai_integration.dto.AiDtos.QuestionSetResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.RegeneratePreviewResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.RegenerateQuestionRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.SaveQuestionSetRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.UpdateQuestionRequest;
import com.smartedu.modules.ai_integration.entity.AiCallLog;
import com.smartedu.modules.ai_integration.entity.Question;
import com.smartedu.modules.ai_integration.entity.QuestionOption;
import com.smartedu.modules.ai_integration.entity.QuestionSet;
import com.smartedu.modules.ai_integration.repository.AiCallLogRepository;
import com.smartedu.modules.ai_integration.repository.QuestionRepository;
import com.smartedu.modules.ai_integration.repository.QuestionSetRepository;
import com.smartedu.modules.learning.entity.LearningMaterial;
import com.smartedu.modules.learning.entity.MaterialContent;
import com.smartedu.modules.learning.entity.QuestionGenerationRequest;
import com.smartedu.modules.learning.repository.LearningMaterialRepository;
import com.smartedu.modules.learning.repository.MaterialContentRepository;
import com.smartedu.modules.learning.repository.QuestionGenerationRequestRepository;
import com.smartedu.modules.user_management.entity.User;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionService {

    private static final Set<String> SUPPORTED_QUESTION_TYPES = Set.of("MULTIPLE_CHOICE", "TRUE_FALSE", "MIXED", "BOTH");
    private static final Set<String> SUPPORTED_DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD", "MIXED");
    private static final String STATUS_PENDING_REPLACEMENT = "PENDING_REPLACEMENT";
    private static final Pattern PAGE_RANGE_PATTERN = Pattern.compile("(\\d+)\\s*[-–]\\s*(\\d+)");

    private final LearningMaterialRepository materialRepository;
    private final MaterialContentRepository contentRepository;
    private final QuestionGenerationRequestRepository genRequestRepository;
    private final QuestionSetRepository questionSetRepository;
    private final QuestionRepository questionRepository;
    private final AiCallLogRepository aiCallLogRepository;
    private final ObjectMapper objectMapper;
    private final PlatformTransactionManager transactionManager;

    @Value("${smartedu.ai.provider:GEMINI}")
    private String aiProvider;

    @Value("${smartedu.ai.model:gemini-2.0-flash}")
    private String aiModel;

    @Value("${smartedu.ai.api-key:}")
    private String aiApiKey;

    @Value("${smartedu.ai.timeout-ms:45000}")
    private long aiTimeoutMs;

    public QuestionSetResponse generateQuestions(User user, GenerateQuestionsRequest req) {
        if (req.getQuestionCount() == null || req.getQuestionCount() < 1 || req.getQuestionCount() > 20) {
            throw new BusinessException("Số lượng câu hỏi phải nằm trong khoảng từ 1 đến 20 câu.");
        }

        String normalizedType = normalizeQuestionType(req.getQuestionType());
        String normalizedDifficulty = normalizeDifficulty(req.getDifficulty());
        String normalizedScope = normalizeScope(req.getContentScope());

        req.setQuestionType(normalizedType);
        req.setDifficulty(normalizedDifficulty);
        req.setContentScope(normalizedScope);

        // Phase 1: Validate material & persist PROCESSING status immediately in its own transaction
        PreparedGeneration prepared = inNewTransaction(() -> prepareGenerationRequest(user, req));

        long startMs = System.currentTimeMillis();
        List<GeneratedQuestionItem> generatedItems;
        try {
            if (aiApiKey == null || aiApiKey.isBlank()) {
                throw new BusinessException(
                        "Chưa cấu hình khóa API Gemini. Vui lòng đặt SMARTEDU_AI_API_KEY trước khi sử dụng AI.",
                        HttpStatus.SERVICE_UNAVAILABLE);
            }

            String scopedText = applyContentScope(prepared.extractedText(), normalizedScope, req.getScopeDescription());
            generatedItems = callGeminiApi(
                    scopedText,
                    req.getQuestionCount(),
                    normalizedType,
                    normalizedDifficulty,
                    normalizedScope,
                    req.getScopeDescription(),
                    List.of(),
                    null);

            if (generatedItems == null || generatedItems.isEmpty()) {
                throw new BusinessException("AI không trả về câu hỏi hợp lệ.", HttpStatus.BAD_GATEWAY);
            }

            if (generatedItems.size() > req.getQuestionCount()) {
                generatedItems = new ArrayList<>(generatedItems.subList(0, req.getQuestionCount()));
            }
        } catch (BusinessException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            inNewTransaction(() -> {
                markGenerationFailed(user, prepared.requestId(), e.getMessage(), elapsed, "AI_GENERATION_ERROR");
                return null;
            });
            throw e;
        } catch (HttpTimeoutException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            String msg = "Dịch vụ AI phản hồi quá thời gian cho phép. Vui lòng thử lại.";
            inNewTransaction(() -> {
                markGenerationFailed(user, prepared.requestId(), msg, elapsed, "AI_TIMEOUT");
                return null;
            });
            throw new BusinessException(msg, HttpStatus.GATEWAY_TIMEOUT);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            String msg = "Không thể tạo câu hỏi từ AI: " + e.getMessage();
            inNewTransaction(() -> {
                markGenerationFailed(user, prepared.requestId(), msg, elapsed, "AI_API_ERROR");
                return null;
            });
            throw new BusinessException(msg, HttpStatus.BAD_GATEWAY);
        }

        long elapsed = System.currentTimeMillis() - startMs;
        List<GeneratedQuestionItem> finalItems = generatedItems;
        return inNewTransaction(() -> completeGeneration(user, prepared, req, finalItems, elapsed));
    }

    @Transactional(readOnly = true)
    public GenerationStatusResponse getGenerationRequestStatus(User user, Long requestId) {
        QuestionGenerationRequest req = genRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu tạo câu hỏi.", HttpStatus.NOT_FOUND));
        if (!req.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền xem yêu cầu tạo câu hỏi này.", HttpStatus.FORBIDDEN);
        }
        Long setId = questionSetRepository.findByGenerationRequestId(req.getId())
                .map(QuestionSet::getId)
                .orElse(null);
        return GenerationStatusResponse.builder()
                .requestId(req.getId())
                .learningMaterialId(req.getLearningMaterial().getId())
                .contentScope(req.getContentScope())
                .scopeDescription(req.getScopeDescription())
                .questionType(req.getQuestionType())
                .questionCount(req.getQuestionCount())
                .difficulty(req.getDifficulty())
                .status(req.getStatus())
                .requestedAt(req.getRequestedAt())
                .completedAt(req.getCompletedAt())
                .errorMessage(req.getErrorMessage())
                .questionSetId(setId)
                .build();
    }

    public QuestionSetResponse retryGenerationRequest(User user, Long requestId) {
        QuestionGenerationRequest existing = genRequestRepository.findById(requestId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu tạo câu hỏi.", HttpStatus.NOT_FOUND));
        if (!existing.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền thử lại yêu cầu tạo câu hỏi này.", HttpStatus.FORBIDDEN);
        }
        if ("COMPLETED".equalsIgnoreCase(existing.getStatus())) {
            QuestionSet qs = questionSetRepository.findByGenerationRequestId(existing.getId())
                    .orElseThrow(() -> new BusinessException("Yêu cầu đã hoàn thành nhưng không tìm thấy bộ câu hỏi.", HttpStatus.NOT_FOUND));
            return toSetResponse(qs, existing.getQuestionCount());
        }

        GenerateQuestionsRequest req = new GenerateQuestionsRequest();
        req.setGenerationRequestId(existing.getId());
        req.setLearningMaterialId(existing.getLearningMaterial().getId());
        req.setContentScope(existing.getContentScope());
        req.setScopeDescription(existing.getScopeDescription());
        req.setQuestionType(existing.getQuestionType());
        req.setQuestionCount(existing.getQuestionCount());
        req.setDifficulty(existing.getDifficulty());
        return generateQuestions(user, req);
    }

    @Transactional(readOnly = true)
    public QuestionDto getQuestionDetail(User user, Long questionId) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy câu hỏi.", HttpStatus.NOT_FOUND));
        if (!q.getQuestionSet().getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền truy cập câu hỏi này.", HttpStatus.FORBIDDEN);
        }
        return toQuestionDto(q);
    }

    @Transactional
    public QuestionDto updateQuestion(User user, Long questionId, UpdateQuestionRequest req) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy câu hỏi.", HttpStatus.NOT_FOUND));

        if (!q.getQuestionSet().getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền chỉnh sửa câu hỏi này.", HttpStatus.FORBIDDEN);
        }
        if (STATUS_PENDING_REPLACEMENT.equals(q.getQuestionStatus())) {
            throw new BusinessException("Không thể chỉnh sửa câu hỏi tạm đang chờ xác nhận.");
        }

        String trimmedContent = req.getContent() != null ? req.getContent().trim() : "";
        if (trimmedContent.isBlank()) {
            throw new BusinessException("Nội dung câu hỏi không được để trống.");
        }

        if (req.getDifficulty() != null && !req.getDifficulty().isBlank()) {
            String diff = normalizeDifficulty(req.getDifficulty());
            if ("MIXED".equals(diff)) {
                throw new BusinessException("Mức độ khó của từng câu hỏi phải là EASY, MEDIUM hoặc HARD.");
            }
            q.setDifficulty(diff);
        }

        if ("TRUE_FALSE".equalsIgnoreCase(q.getQuestionType())) {
            validateAndApplyTrueFalseOptions(q, req);
        } else {
            validateAndApplyMultipleChoiceOptions(q, req);
        }

        q.setContent(trimmedContent);
        if (req.getExplanation() != null) {
            String exp = req.getExplanation().trim();
            q.setExplanation(exp.isEmpty() ? null : exp);
        }
        q.setQuestionStatus("MANUALLY_REVIEWED");

        Question saved = questionRepository.save(q);
        return toQuestionDto(saved);
    }

    /**
     * PB14: Generates a replacement candidate and stores it as PENDING_REPLACEMENT preview unless confirmImmediately=true.
     */
    public Object regenerateQuestion(User user, Long questionId, RegenerateQuestionRequest req) {
        RegenerateQuestionRequest effectiveReq = req != null ? req : new RegenerateQuestionRequest();

        PreparedRegeneration prep = inNewTransaction(() -> prepareRegeneration(user, questionId, effectiveReq));

        long startMs = System.currentTimeMillis();
        GeneratedQuestionItem item;
        try {
            if (aiApiKey == null || aiApiKey.isBlank()) {
                throw new BusinessException(
                        "Chưa cấu hình khóa API Gemini. Vui lòng đặt SMARTEDU_AI_API_KEY trước khi sử dụng AI.",
                        HttpStatus.SERVICE_UNAVAILABLE);
            }

            String scopedText = applyContentScope(prep.extractedText(), prep.contentScope(), prep.scopeDescription());
            List<GeneratedQuestionItem> items = callGeminiApi(
                    scopedText,
                    1,
                    prep.questionType(),
                    prep.difficulty(),
                    prep.contentScope(),
                    prep.scopeDescription(),
                    prep.avoidContents(),
                    effectiveReq.getAdditionalRequirement());
            item = items.stream().findFirst()
                    .orElseThrow(() -> new BusinessException("AI không trả về câu hỏi thay thế hợp lệ.", HttpStatus.BAD_GATEWAY));
        } catch (BusinessException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            inNewTransaction(() -> {
                logRegenerationFailure(user, prep.generationRequestId(), e.getMessage(), elapsed, "AI_REGEN_ERROR");
                return null;
            });
            throw e;
        } catch (HttpTimeoutException e) {
            long elapsed = System.currentTimeMillis() - startMs;
            String msg = "Dịch vụ AI phản hồi quá thời gian khi tạo lại câu hỏi. Câu hỏi cũ vẫn được giữ nguyên.";
            inNewTransaction(() -> {
                logRegenerationFailure(user, prep.generationRequestId(), msg, elapsed, "AI_TIMEOUT");
                return null;
            });
            throw new BusinessException(msg, HttpStatus.GATEWAY_TIMEOUT);
        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startMs;
            String msg = "Không thể tạo lại câu hỏi bằng AI: " + e.getMessage();
            inNewTransaction(() -> {
                logRegenerationFailure(user, prep.generationRequestId(), msg, elapsed, "AI_API_ERROR");
                return null;
            });
            throw new BusinessException(msg, HttpStatus.BAD_GATEWAY);
        }

        long elapsed = System.currentTimeMillis() - startMs;
        if (Boolean.TRUE.equals(effectiveReq.getConfirmImmediately())) {
            return inNewTransaction(() -> applyRegenerationImmediately(user, prep, item, elapsed));
        }
        return inNewTransaction(() -> createRegenerationPreview(user, prep, item, elapsed));
    }

    @Transactional
    public QuestionDto confirmRegenerate(User user, Long questionId, Long previewQuestionId) {
        Question oldQ = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy câu hỏi gốc.", HttpStatus.NOT_FOUND));
        if (!oldQ.getQuestionSet().getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền xác nhận thay thế câu hỏi này.", HttpStatus.FORBIDDEN);
        }

        Question preview = questionRepository.findById(previewQuestionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy bản xem trước câu hỏi.", HttpStatus.NOT_FOUND));
        if (!STATUS_PENDING_REPLACEMENT.equals(preview.getQuestionStatus())
                || preview.getReplacesQuestion() == null
                || !preview.getReplacesQuestion().getId().equals(oldQ.getId())) {
            throw new BusinessException("Bản xem trước không thuộc câu hỏi được chọn.");
        }

        copyQuestionContentAndOptions(preview, oldQ);
        oldQ.setQuestionStatus("ACTIVE");
        questionRepository.delete(preview);
        questionRepository.flush();

        return toQuestionDto(questionRepository.save(oldQ));
    }

    @Transactional
    public QuestionDto rejectRegenerate(User user, Long questionId, Long previewQuestionId) {
        Question oldQ = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy câu hỏi gốc.", HttpStatus.NOT_FOUND));
        if (!oldQ.getQuestionSet().getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền thao tác câu hỏi này.", HttpStatus.FORBIDDEN);
        }

        if (previewQuestionId != null) {
            Question preview = questionRepository.findById(previewQuestionId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy bản xem trước câu hỏi.", HttpStatus.NOT_FOUND));
            if (!STATUS_PENDING_REPLACEMENT.equals(preview.getQuestionStatus())
                    || preview.getReplacesQuestion() == null
                    || !preview.getReplacesQuestion().getId().equals(oldQ.getId())) {
                throw new BusinessException("Bản xem trước không thuộc câu hỏi được chọn.");
            }
            questionRepository.delete(preview);
        } else {
            List<Question> previews = questionRepository.findByReplacesQuestionIdAndQuestionStatus(
                    oldQ.getId(), STATUS_PENDING_REPLACEMENT);
            if (!previews.isEmpty()) {
                questionRepository.deleteAll(previews);
            }
        }
        return toQuestionDto(oldQ);
    }

    @Transactional
    public QuestionSetResponse saveQuestionSet(User user, Long setId, SaveQuestionSetRequest req) {
        QuestionSet qs = findOwnedQuestionSet(user, setId);

        List<Question> activeQuestions = getActiveQuestions(qs);
        if (activeQuestions.isEmpty()) {
            throw new BusinessException("Bộ câu hỏi rỗng, không thể lưu.");
        }

        String title = req.getTitle() != null ? req.getTitle().trim() : "";
        if (title.isBlank()) {
            throw new BusinessException("Vui lòng nhập tên bộ câu hỏi.");
        }
        if (title.length() > 255) {
            throw new BusinessException("Tên bộ câu hỏi không được vượt quá 255 ký tự.");
        }

        qs.setTitle(title);
        if (req.getDescription() != null) {
            qs.setDescription(req.getDescription().trim());
        }
        qs.setSetStatus("SAVED");
        qs.setSavedAt(LocalDateTime.now());

        return toSetResponse(questionSetRepository.save(qs), null);
    }

    @Transactional(readOnly = true)
    public List<QuestionSetResponse> listMyQuestionSets(User user, String status) {
        List<QuestionSet> sets;
        if (status != null && !status.isBlank()) {
            String normalized = status.trim().toUpperCase(Locale.ROOT);
            if ("SAVED".equals(normalized)) {
                sets = questionSetRepository.findByUserIdAndSetStatusInOrderByCreatedAtDesc(
                        user.getId(), List.of("SAVED", "PUBLISHED"));
            } else {
                sets = questionSetRepository.findByUserIdAndSetStatusInOrderByCreatedAtDesc(
                        user.getId(), List.of(normalized));
            }
        } else {
            sets = questionSetRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        }
        return sets.stream().map(qs -> toSetResponse(qs, null)).toList();
    }

    @Transactional(readOnly = true)
    public QuestionSetResponse getQuestionSetDetail(User user, Long setId) {
        QuestionSet qs = findOwnedQuestionSet(user, setId);
        return toSetResponse(qs, null);
    }

    private QuestionSet findOwnedQuestionSet(User user, Long setId) {
        QuestionSet qs = questionSetRepository.findById(setId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy bộ câu hỏi.", HttpStatus.NOT_FOUND));
        if (!qs.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền truy cập bộ câu hỏi này.", HttpStatus.FORBIDDEN);
        }
        return qs;
    }

    private record PreparedGeneration(
            Long requestId,
            Long materialId,
            String materialTitle,
            String originalFileName,
            String extractedText
    ) {}

    private record PreparedRegeneration(
            Long oldQuestionId,
            Long questionSetId,
            Long generationRequestId,
            String extractedText,
            String questionType,
            String difficulty,
            String contentScope,
            String scopeDescription,
            List<String> avoidContents
    ) {}

    private PreparedGeneration prepareGenerationRequest(User user, GenerateQuestionsRequest req) {
        LearningMaterial material = materialRepository.findById(req.getLearningMaterialId())
                .filter(m -> m.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException("Không tìm thấy học liệu.", HttpStatus.NOT_FOUND));
        if (!material.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền sử dụng học liệu này.", HttpStatus.FORBIDDEN);
        }
        if (!"COMPLETED".equalsIgnoreCase(material.getProcessingStatus())) {
            throw new BusinessException("Học liệu chưa được xử lý hoàn tất.");
        }

        MaterialContent content = contentRepository.findByLearningMaterialId(material.getId())
                .orElseThrow(() -> new BusinessException("Học liệu chưa có nội dung văn bản trích xuất."));
        if (!"COMPLETED".equalsIgnoreCase(content.getExtractionStatus())
                || content.getExtractedText() == null
                || content.getExtractedText().isBlank()) {
            throw new BusinessException("Nội dung văn bản của học liệu đang trống, không thể tạo câu hỏi.");
        }

        QuestionGenerationRequest genReq;
        if (req.getGenerationRequestId() != null) {
            genReq = genRequestRepository.findByIdAndUserId(req.getGenerationRequestId(), user.getId())
                    .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu tạo câu hỏi.", HttpStatus.NOT_FOUND));
            genReq.setLearningMaterial(material);
            genReq.setContentScope(req.getContentScope());
            genReq.setScopeDescription(req.getScopeDescription());
            genReq.setQuestionType(req.getQuestionType());
            genReq.setQuestionCount(req.getQuestionCount());
            genReq.setDifficulty(req.getDifficulty());
            genReq.setErrorMessage(null);
            genReq.setCompletedAt(null);
        } else {
            genReq = new QuestionGenerationRequest();
            genReq.setUser(user);
            genReq.setLearningMaterial(material);
            genReq.setContentScope(req.getContentScope());
            genReq.setScopeDescription(req.getScopeDescription());
            genReq.setQuestionType(req.getQuestionType());
            genReq.setQuestionCount(req.getQuestionCount());
            genReq.setDifficulty(req.getDifficulty());
        }
        genReq.setStatus("PROCESSING");
        genReq = genRequestRepository.save(genReq);

        return new PreparedGeneration(
                genReq.getId(),
                material.getId(),
                material.getTitle(),
                material.getOriginalFileName(),
                content.getExtractedText());
    }

    private void markGenerationFailed(User user, Long requestId, String errorMessage, long elapsedMs, String errorCode) {
        QuestionGenerationRequest genReq = genRequestRepository.findById(requestId).orElse(null);
        if (genReq != null) {
            genReq.setStatus("FAILED");
            genReq.setCompletedAt(LocalDateTime.now());
            genReq.setErrorMessage(errorMessage);
            genRequestRepository.save(genReq);
        }
        aiCallLogRepository.save(AiCallLog.builder()
                .user(user)
                .questionGenerationRequest(genReq)
                .operationType("QUESTION_GENERATION")
                .provider(aiProvider)
                .modelName(aiModel)
                .responseTimeMs(elapsedMs)
                .callStatus("FAILED")
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .calledAt(LocalDateTime.now())
                .build());
    }

    private QuestionSetResponse completeGeneration(
            User user,
            PreparedGeneration prepared,
            GenerateQuestionsRequest req,
            List<GeneratedQuestionItem> generatedItems,
            long elapsedMs) {
        QuestionGenerationRequest genReq = genRequestRepository.findById(prepared.requestId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu tạo câu hỏi."));
        LearningMaterial material = materialRepository.getReferenceById(prepared.materialId());

        genReq.setStatus("COMPLETED");
        genReq.setCompletedAt(LocalDateTime.now());
        genReq.setErrorMessage(null);
        genRequestRepository.save(genReq);

        long promptTokens = Math.min(4000L, prepared.extractedText().length() / 4L);
        long completionTokens = generatedItems.size() * 140L;
        aiCallLogRepository.save(AiCallLog.builder()
                .user(user)
                .questionGenerationRequest(genReq)
                .operationType("QUESTION_GENERATION")
                .provider(aiProvider)
                .modelName(aiModel)
                .responseTimeMs(elapsedMs)
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .totalTokens(promptTokens + completionTokens)
                .callStatus("SUCCESS")
                .calledAt(LocalDateTime.now())
                .build());

        String setTitle = (req.getSetTitle() != null && !req.getSetTitle().isBlank())
                ? req.getSetTitle().trim()
                : "Bộ câu hỏi ôn tập: " + prepared.materialTitle();

        QuestionSet questionSet = QuestionSet.builder()
                .user(user)
                .generationRequest(genReq)
                .sourceMaterial(material)
                .title(setTitle)
                .description("Tạo tự động từ học liệu " + prepared.originalFileName()
                        + " (" + generatedItems.size() + "/" + req.getQuestionCount() + " câu)")
                .setStatus("DRAFT")
                .build();
        questionSet = questionSetRepository.save(questionSet);

        int order = 1;
        for (GeneratedQuestionItem item : generatedItems) {
            Question q = Question.builder()
                    .questionSet(questionSet)
                    .sourceMaterial(material)
                    .questionType(item.questionType())
                    .content(item.content())
                    .explanation(item.explanation())
                    .difficulty(item.difficulty())
                    .referenceExcerpt(item.referenceExcerpt())
                    .referencePage(item.referencePage())
                    .displayOrder(order++)
                    .questionStatus("ACTIVE")
                    .build();

            int optOrder = 1;
            for (GeneratedOptionItem opt : item.options()) {
                q.getOptions().add(QuestionOption.builder()
                        .question(q)
                        .optionLabel(opt.label())
                        .content(opt.content())
                        .isCorrect(opt.isCorrect())
                        .displayOrder(optOrder++)
                        .build());
            }
            questionSet.getQuestions().add(q);
        }

        questionSet = questionSetRepository.save(questionSet);
        return toSetResponse(questionSet, req.getQuestionCount());
    }

    private PreparedRegeneration prepareRegeneration(User user, Long questionId, RegenerateQuestionRequest req) {
        Question oldQ = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy câu hỏi.", HttpStatus.NOT_FOUND));
        if (!oldQ.getQuestionSet().getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền thao tác câu hỏi này.", HttpStatus.FORBIDDEN);
        }
        if (STATUS_PENDING_REPLACEMENT.equals(oldQ.getQuestionStatus())) {
            throw new BusinessException("Không thể tạo lại từ một bản xem trước.");
        }

        LearningMaterial sourceMaterial = oldQ.getSourceMaterial() != null
                ? oldQ.getSourceMaterial()
                : oldQ.getQuestionSet().getSourceMaterial();
        if (sourceMaterial == null) {
            throw new BusinessException("Câu hỏi không gắn với học liệu nguồn để tạo lại.");
        }

        MaterialContent content = contentRepository.findByLearningMaterialId(sourceMaterial.getId())
                .orElseThrow(() -> new BusinessException("Học liệu chưa có nội dung văn bản trích xuất."));
        if (content.getExtractedText() == null || content.getExtractedText().isBlank()) {
            throw new BusinessException("Nội dung văn bản của học liệu đang trống.");
        }

        QuestionGenerationRequest genReq = oldQ.getQuestionSet().getGenerationRequest();
        String scope = genReq != null ? genReq.getContentScope() : "FULL_DOCUMENT";
        String scopeDesc = genReq != null ? genReq.getScopeDescription() : null;
        String difficulty = (req.getDifficulty() != null && !req.getDifficulty().isBlank())
                ? normalizeDifficulty(req.getDifficulty())
                : (oldQ.getDifficulty() != null ? oldQ.getDifficulty() : "MEDIUM");

        List<String> avoidContents = getActiveQuestions(oldQ.getQuestionSet()).stream()
                .map(Question::getContent)
                .toList();

        return new PreparedRegeneration(
                oldQ.getId(),
                oldQ.getQuestionSet().getId(),
                genReq != null ? genReq.getId() : null,
                content.getExtractedText(),
                oldQ.getQuestionType(),
                difficulty,
                scope,
                scopeDesc,
                avoidContents);
    }

    private void logRegenerationFailure(User user, Long generationRequestId, String message, long elapsedMs, String errorCode) {
        QuestionGenerationRequest genReq = generationRequestId != null
                ? genRequestRepository.findById(generationRequestId).orElse(null)
                : null;
        aiCallLogRepository.save(AiCallLog.builder()
                .user(user)
                .questionGenerationRequest(genReq)
                .operationType("QUESTION_REGENERATION")
                .provider(aiProvider)
                .modelName(aiModel)
                .responseTimeMs(elapsedMs)
                .callStatus("FAILED")
                .errorCode(errorCode)
                .errorMessage(message)
                .calledAt(LocalDateTime.now())
                .build());
    }

    private QuestionDto applyRegenerationImmediately(
            User user, PreparedRegeneration prep, GeneratedQuestionItem item, long elapsedMs) {
        Question oldQ = questionRepository.findById(prep.oldQuestionId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy câu hỏi.", HttpStatus.NOT_FOUND));

        oldQ.setContent(item.content());
        oldQ.setExplanation(item.explanation());
        oldQ.setDifficulty(item.difficulty());
        oldQ.setReferenceExcerpt(item.referenceExcerpt());
        oldQ.setReferencePage(item.referencePage());
        oldQ.setQuestionStatus("ACTIVE");
        replaceOptionsSafely(oldQ, item.options());

        logRegenerationSuccess(user, prep.generationRequestId(), elapsedMs);
        return toQuestionDto(questionRepository.save(oldQ));
    }

    private RegeneratePreviewResponse createRegenerationPreview(
            User user, PreparedRegeneration prep, GeneratedQuestionItem item, long elapsedMs) {
        Question oldQ = questionRepository.findById(prep.oldQuestionId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy câu hỏi.", HttpStatus.NOT_FOUND));

        List<Question> existingPreviews = questionRepository.findByReplacesQuestionIdAndQuestionStatus(
                oldQ.getId(), STATUS_PENDING_REPLACEMENT);
        if (!existingPreviews.isEmpty()) {
            questionRepository.deleteAll(existingPreviews);
            questionRepository.flush();
        }

        int previewOrder = -Math.abs(oldQ.getId().intValue());
        Question preview = Question.builder()
                .questionSet(oldQ.getQuestionSet())
                .sourceMaterial(oldQ.getSourceMaterial())
                .replacesQuestion(oldQ)
                .questionType(oldQ.getQuestionType())
                .content(item.content())
                .explanation(item.explanation())
                .difficulty(item.difficulty())
                .referenceExcerpt(item.referenceExcerpt())
                .referencePage(item.referencePage())
                .displayOrder(previewOrder)
                .questionStatus(STATUS_PENDING_REPLACEMENT)
                .build();

        int optOrder = 1;
        for (GeneratedOptionItem opt : item.options()) {
            preview.getOptions().add(QuestionOption.builder()
                    .question(preview)
                    .optionLabel(opt.label())
                    .content(opt.content())
                    .isCorrect(opt.isCorrect())
                    .displayOrder(optOrder++)
                    .build());
        }

        preview = questionRepository.save(preview);
        logRegenerationSuccess(user, prep.generationRequestId(), elapsedMs);

        return RegeneratePreviewResponse.builder()
                .originalQuestionId(oldQ.getId())
                .previewQuestionId(preview.getId())
                .questionSetId(oldQ.getQuestionSet().getId())
                .originalQuestion(toQuestionDto(oldQ))
                .previewQuestion(toQuestionDto(preview))
                .build();
    }

    private void logRegenerationSuccess(User user, Long generationRequestId, long elapsedMs) {
        QuestionGenerationRequest genReq = generationRequestId != null
                ? genRequestRepository.findById(generationRequestId).orElse(null)
                : null;
        aiCallLogRepository.save(AiCallLog.builder()
                .user(user)
                .questionGenerationRequest(genReq)
                .operationType("QUESTION_REGENERATION")
                .provider(aiProvider)
                .modelName(aiModel)
                .responseTimeMs(elapsedMs)
                .callStatus("SUCCESS")
                .calledAt(LocalDateTime.now())
                .build());
    }

    private void copyQuestionContentAndOptions(Question source, Question target) {
        target.setContent(source.getContent());
        target.setExplanation(source.getExplanation());
        target.setDifficulty(source.getDifficulty());
        target.setReferenceExcerpt(source.getReferenceExcerpt());
        target.setReferencePage(source.getReferencePage());

        target.getOptions().clear();
        questionRepository.saveAndFlush(target);

        int idx = 1;
        for (QuestionOption opt : source.getOptions()) {
            target.getOptions().add(QuestionOption.builder()
                    .question(target)
                    .optionLabel(opt.getOptionLabel())
                    .content(opt.getContent())
                    .isCorrect(Boolean.TRUE.equals(opt.getIsCorrect()))
                    .displayOrder(idx++)
                    .build());
        }
    }

    private void replaceOptionsSafely(Question q, List<GeneratedOptionItem> items) {
        q.getOptions().clear();
        questionRepository.saveAndFlush(q);

        int idx = 1;
        for (GeneratedOptionItem opt : items) {
            q.getOptions().add(QuestionOption.builder()
                    .question(q)
                    .optionLabel(opt.label())
                    .content(opt.content())
                    .isCorrect(opt.isCorrect())
                    .displayOrder(idx++)
                    .build());
        }
    }

    private void validateAndApplyTrueFalseOptions(Question q, UpdateQuestionRequest req) {
        if (req.getOptions() != null && !req.getOptions().isEmpty()) {
            if (req.getOptions().size() != 2) {
                throw new BusinessException("Câu hỏi Đúng/Sai phải có đúng 2 phương án Đúng và Sai.");
            }
            long correctCount = req.getOptions().stream()
                    .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                    .count();
            if (correctCount != 1) {
                throw new BusinessException("Câu hỏi Đúng/Sai phải có đúng 1 đáp án chính xác.");
            }

            boolean hasTrue = false;
            boolean hasFalse = false;
            List<GeneratedOptionItem> normalized = new ArrayList<>();
            for (OptionDto o : req.getOptions()) {
                String content = o.getContent() != null ? o.getContent().trim() : "";
                String label = o.getOptionLabel() != null ? o.getOptionLabel().trim() : "";
                if (isTrueValue(content) || isTrueValue(label)) {
                    hasTrue = true;
                    normalized.add(new GeneratedOptionItem(
                            label.isBlank() ? "A" : label,
                            content.isBlank() ? "Đúng" : content,
                            Boolean.TRUE.equals(o.getIsCorrect())));
                } else if (isFalseValue(content) || isFalseValue(label)) {
                    hasFalse = true;
                    normalized.add(new GeneratedOptionItem(
                            label.isBlank() ? "B" : label,
                            content.isBlank() ? "Sai" : content,
                            Boolean.TRUE.equals(o.getIsCorrect())));
                } else {
                    throw new BusinessException("Đáp án câu hỏi Đúng/Sai chỉ được nhận giá trị Đúng hoặc Sai.");
                }
            }
            if (!hasTrue || !hasFalse) {
                throw new BusinessException("Câu hỏi Đúng/Sai phải gồm một phương án Đúng và một phương án Sai.");
            }
            replaceOptionsSafely(q, normalized);
        } else if (req.getCorrectAnswer() != null && !req.getCorrectAnswer().isBlank()) {
            String ans = req.getCorrectAnswer().trim();
            boolean targetTrue;
            if (isTrueValue(ans)) {
                targetTrue = true;
            } else if (isFalseValue(ans)) {
                targetTrue = false;
            } else {
                throw new BusinessException("Đáp án câu hỏi Đúng/Sai chỉ được nhận giá trị Đúng hoặc Sai.");
            }
            for (QuestionOption opt : q.getOptions()) {
                boolean isOptTrue = isTrueValue(opt.getContent()) || isTrueValue(opt.getOptionLabel());
                opt.setIsCorrect(targetTrue == isOptTrue);
            }
        }
    }

    private void validateAndApplyMultipleChoiceOptions(Question q, UpdateQuestionRequest req) {
        if (req.getOptions() == null || req.getOptions().isEmpty()) {
            return;
        }
        if (req.getOptions().size() < 2) {
            throw new BusinessException("Câu hỏi trắc nghiệm phải có ít nhất 2 phương án lựa chọn.");
        }
        long correctCount = req.getOptions().stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .count();
        if (correctCount != 1) {
            throw new BusinessException("Mỗi câu hỏi trắc nghiệm phải có đúng 1 đáp án đúng.");
        }

        Set<String> seenLabels = new HashSet<>();
        Set<String> seenContents = new HashSet<>();
        List<GeneratedOptionItem> items = new ArrayList<>();
        int fallbackIdx = 0;
        for (OptionDto o : req.getOptions()) {
            String content = o.getContent() != null ? o.getContent().trim() : "";
            if (content.isBlank()) {
                throw new BusinessException("Nội dung các phương án lựa chọn không được để trống.");
            }
            String label = (o.getOptionLabel() != null && !o.getOptionLabel().isBlank())
                    ? o.getOptionLabel().trim().toUpperCase(Locale.ROOT)
                    : String.valueOf((char) ('A' + fallbackIdx));
            fallbackIdx++;

            if (!seenLabels.add(label)) {
                throw new BusinessException("Nhãn phương án '" + label + "' bị trùng lặp.");
            }
            if (!seenContents.add(content.toLowerCase(Locale.ROOT))) {
                throw new BusinessException("Nội dung các phương án lựa chọn không được trùng lặp.");
            }
            items.add(new GeneratedOptionItem(label, content, Boolean.TRUE.equals(o.getIsCorrect())));
        }
        replaceOptionsSafely(q, items);
    }

    protected record GeneratedOptionItem(String label, String content, boolean isCorrect) {}

    protected record GeneratedQuestionItem(
            String questionType,
            String content,
            String explanation,
            String difficulty,
            String referenceExcerpt,
            Long referencePage,
            List<GeneratedOptionItem> options
    ) {}

    protected List<GeneratedQuestionItem> callGeminiApi(
            String extractedText,
            int questionCount,
            String questionType,
            String difficulty,
            String contentScope,
            String scopeDescription,
            List<String> avoidContents,
            String additionalRequirement) throws Exception {

        String context = truncate(extractedText, 12000);
        String prompt = buildPrompt(
                context, questionCount, questionType, difficulty,
                contentScope, scopeDescription, avoidContents, additionalRequirement);

        String responseBody = invokeGeminiHttp(prompt);
        return parseGeminiResponse(responseBody, questionType, avoidContents);
    }

    protected String invokeGeminiHttp(String prompt) throws Exception {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );

        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + aiModel + ":generateContent?key=" + aiApiKey;
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(aiTimeoutMs)).build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(aiTimeoutMs))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new RuntimeException("Gemini API HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private String buildPrompt(
            String context,
            int questionCount,
            String questionType,
            String difficulty,
            String contentScope,
            String scopeDescription,
            List<String> avoidContents,
            String additionalRequirement) {
        StringBuilder sb = new StringBuilder();
        sb.append("Bạn là trợ lý khảo thí đại học SmartEdu. Hãy tạo đúng ").append(questionCount)
                .append(" câu hỏi ôn tập bám sát 100% nội dung học liệu sau, không trùng lặp nội dung.\n");

        if ("TRUE_FALSE".equals(questionType)) {
            sb.append("- Dạng câu hỏi: TRUE_FALSE (Đúng/Sai). Mỗi câu hỏi là một nhận định rõ ràng và CHỈ CÓ ĐÚNG 2 phương án: "
                    + "{\"label\":\"A\",\"content\":\"Đúng\",\"isCorrect\":...} và {\"label\":\"B\",\"content\":\"Sai\",\"isCorrect\":...}.\n");
        } else if ("MIXED".equals(questionType) || "BOTH".equals(questionType)) {
            sb.append("- Dạng câu hỏi: Kết hợp cả MULTIPLE_CHOICE (4 phương án A,B,C,D, đúng 1 đáp án đúng) và TRUE_FALSE (2 phương án Đúng/Sai). "
                    + "Ghi rõ \"questionType\" từng câu là \"MULTIPLE_CHOICE\" hoặc \"TRUE_FALSE\".\n");
        } else {
            sb.append("- Dạng câu hỏi: MULTIPLE_CHOICE (Trắc nghiệm 4 lựa chọn A, B, C, D với duy nhất 1 đáp án đúng).\n");
        }

        sb.append("- Độ khó: ").append(difficulty).append(".\n");
        sb.append("- Phạm vi nội dung: ").append(contentScope);
        if (scopeDescription != null && !scopeDescription.isBlank()) {
            sb.append(" (").append(scopeDescription.trim()).append(")");
        }
        sb.append(".\n");

        if (additionalRequirement != null && !additionalRequirement.isBlank()) {
            sb.append("- Yêu cầu bổ sung: ").append(additionalRequirement.trim()).append("\n");
        }
        if (avoidContents != null && !avoidContents.isEmpty()) {
            sb.append("- TUYỆT ĐỐI KHÔNG tạo lại hoặc trùng ý với các câu hỏi sau:\n");
            for (String existing : avoidContents) {
                sb.append("  * ").append(truncate(existing, 200)).append("\n");
            }
        }

        sb.append("- Mỗi câu hỏi cần có \"explanation\" (giải thích ngắn gọn) và \"referenceExcerpt\" (trích đoạn căn cứ trong học liệu nếu có).\n");
        sb.append("Trả về DUY NHẤT một mảng JSON hợp lệ (không markdown, không giải thích ngoài JSON) theo cấu trúc:\n");
        if ("TRUE_FALSE".equals(questionType)) {
            sb.append("[{\"questionType\":\"TRUE_FALSE\",\"content\":\"...\",\"explanation\":\"...\",\"difficulty\":\"MEDIUM\",\"referenceExcerpt\":\"...\",\"referencePage\":1,\"options\":[{\"label\":\"A\",\"content\":\"Đúng\",\"isCorrect\":true},{\"label\":\"B\",\"content\":\"Sai\",\"isCorrect\":false}]}]\n");
        } else {
            sb.append("[{\"questionType\":\"MULTIPLE_CHOICE\",\"content\":\"...\",\"explanation\":\"...\",\"difficulty\":\"MEDIUM\",\"referenceExcerpt\":\"...\",\"referencePage\":1,\"options\":[{\"label\":\"A\",\"content\":\"...\",\"isCorrect\":true},{\"label\":\"B\",\"content\":\"...\",\"isCorrect\":false},{\"label\":\"C\",\"content\":\"...\",\"isCorrect\":false},{\"label\":\"D\",\"content\":\"...\",\"isCorrect\":false}]}]\n");
        }
        sb.append("HỌC LIỆU:\n").append(context);
        return sb.toString();
    }

    private List<GeneratedQuestionItem> parseGeminiResponse(
            String responseBody,
            String requestedType,
            List<String> avoidContents) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            String reason = root.path("promptFeedback").path("blockReason").asText();
            throw new RuntimeException(reason.isBlank()
                    ? "Gemini không trả về nội dung candidates."
                    : "Gemini từ chối nội dung: " + reason);
        }

        StringBuilder rawText = new StringBuilder();
        for (JsonNode part : candidates.get(0).path("content").path("parts")) {
            if (part.has("text")) {
                rawText.append(part.path("text").asText());
            }
        }
        if (rawText.isEmpty()) {
            throw new RuntimeException("Gemini trả về candidates nhưng không có văn bản.");
        }

        String jsonClean = rawText.toString()
                .replaceAll("(?is)^```(?:json)?\\s*", "")
                .replaceAll("(?is)\\s*```$", "")
                .trim();
        int arrayStart = jsonClean.indexOf('[');
        int arrayEnd = jsonClean.lastIndexOf(']');
        if (arrayStart < 0 || arrayEnd < arrayStart) {
            throw new RuntimeException("Gemini không trả về JSON mảng câu hỏi hợp lệ.");
        }
        jsonClean = jsonClean.substring(arrayStart, arrayEnd + 1);

        JsonNode arr = objectMapper.readTree(jsonClean);
        if (!arr.isArray() || arr.isEmpty()) {
            throw new RuntimeException("Gemini không tạo ra câu hỏi.");
        }

        Set<String> seenQuestions = new LinkedHashSet<>();
        if (avoidContents != null) {
            for (String ac : avoidContents) {
                if (ac != null) {
                    seenQuestions.add(normalizeForDedup(ac));
                }
            }
        }

        List<GeneratedQuestionItem> result = new ArrayList<>();
        for (JsonNode node : arr) {
            String content = node.path("content").asText("").trim();
            if (content.isBlank()) {
                continue;
            }
            String dedupKey = normalizeForDedup(content);
            if (!seenQuestions.add(dedupKey)) {
                continue;
            }

            String nodeType = node.path("questionType").asText("").trim().toUpperCase(Locale.ROOT);
            String itemType;
            if ("TRUE_FALSE".equals(requestedType)) {
                itemType = "TRUE_FALSE";
            } else if ("MULTIPLE_CHOICE".equals(requestedType)) {
                itemType = "MULTIPLE_CHOICE";
            } else {
                itemType = "TRUE_FALSE".equals(nodeType) ? "TRUE_FALSE" : "MULTIPLE_CHOICE";
            }

            List<GeneratedOptionItem> opts = new ArrayList<>();
            int idx = 0;
            for (JsonNode o : node.path("options")) {
                String optionContent = o.path("content").asText("").trim();
                if (!optionContent.isBlank()) {
                    String label = o.path("label").asText("").trim();
                    if (label.isBlank()) {
                        label = String.valueOf((char) ('A' + idx));
                    }
                    opts.add(new GeneratedOptionItem(label, optionContent, o.path("isCorrect").asBoolean(false)));
                    idx++;
                }
            }

            if ("TRUE_FALSE".equals(itemType)) {
                opts = normalizeTrueFalseOptions(opts);
                if (opts == null) {
                    continue;
                }
            } else if (opts.size() < 2 || opts.stream().filter(GeneratedOptionItem::isCorrect).count() != 1) {
                continue;
            }

            String explanation = blankToNull(node.path("explanation").asText(null));
            String referenceExcerpt = blankToNull(node.path("referenceExcerpt").asText(null));
            Long referencePage = node.hasNonNull("referencePage") && node.path("referencePage").canConvertToLong()
                    ? node.path("referencePage").asLong()
                    : null;
            String itemDiff = node.path("difficulty").asText("MEDIUM").trim().toUpperCase(Locale.ROOT);
            if (!Set.of("EASY", "MEDIUM", "HARD").contains(itemDiff)) {
                itemDiff = "MEDIUM";
            }

            result.add(new GeneratedQuestionItem(
                    itemType, content, explanation, itemDiff, referenceExcerpt, referencePage, opts));
        }
        return result;
    }

    private List<GeneratedOptionItem> normalizeTrueFalseOptions(List<GeneratedOptionItem> opts) {
        if (opts == null || opts.size() != 2) {
            return null;
        }
        if (opts.stream().filter(GeneratedOptionItem::isCorrect).count() != 1) {
            return null;
        }
        GeneratedOptionItem trueOpt = null;
        GeneratedOptionItem falseOpt = null;
        for (GeneratedOptionItem opt : opts) {
            if (isTrueValue(opt.content()) || isTrueValue(opt.label())) {
                trueOpt = new GeneratedOptionItem("A", "Đúng", opt.isCorrect());
            } else if (isFalseValue(opt.content()) || isFalseValue(opt.label())) {
                falseOpt = new GeneratedOptionItem("B", "Sai", opt.isCorrect());
            }
        }
        if (trueOpt == null || falseOpt == null) {
            return null;
        }
        return List.of(trueOpt, falseOpt);
    }

    private String applyContentScope(String text, String contentScope, String scopeDescription) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String desc = scopeDescription != null ? scopeDescription.toLowerCase(Locale.ROOT) : "";
        int len = text.length();
        if ("FIRST_HALF".equals(contentScope) || desc.contains("nửa đầu")) {
            return text.substring(0, Math.max(1, len / 2));
        }
        if ("SECOND_HALF".equals(contentScope) || desc.contains("nửa sau")) {
            return text.substring(len / 2);
        }
        if (("PAGE_RANGE".equals(contentScope) || "CUSTOM".equals(contentScope)) && !desc.isBlank()) {
            Matcher m = PAGE_RANGE_PATTERN.matcher(desc);
            if (m.find()) {
                int startPage = Math.max(1, Integer.parseInt(m.group(1)));
                int endPage = Math.max(startPage, Integer.parseInt(m.group(2)));
                if (desc.contains("trang") || "PAGE_RANGE".equals(contentScope)) {
                    int approxTotalPages = Math.max(endPage, 10);
                    int startIdx = Math.min(len - 1, (int) ((startPage - 1) / (double) approxTotalPages * len));
                    int endIdx = Math.min(len, (int) (endPage / (double) approxTotalPages * len));
                    if (endIdx > startIdx) {
                        return text.substring(startIdx, endIdx);
                    }
                }
            }
        }
        return text;
    }

    private String normalizeQuestionType(String raw) {
        String val = (raw == null || raw.isBlank()) ? "MULTIPLE_CHOICE" : raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_QUESTION_TYPES.contains(val)) {
            throw new BusinessException("Dạng câu hỏi không hợp lệ. Chỉ hỗ trợ MULTIPLE_CHOICE, TRUE_FALSE hoặc MIXED.");
        }
        return "BOTH".equals(val) ? "MIXED" : val;
    }

    private String normalizeDifficulty(String raw) {
        String val = (raw == null || raw.isBlank()) ? "MIXED" : raw.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_DIFFICULTIES.contains(val)) {
            throw new BusinessException("Mức độ khó không hợp lệ. Chỉ hỗ trợ EASY, MEDIUM, HARD hoặc MIXED.");
        }
        return val;
    }

    private String normalizeScope(String raw) {
        return (raw == null || raw.isBlank()) ? "FULL_DOCUMENT" : raw.trim().toUpperCase(Locale.ROOT);
    }

    private boolean isTrueValue(String s) {
        if (s == null) return false;
        String norm = s.trim().toLowerCase(Locale.ROOT);
        return norm.equals("đúng") || norm.equals("true") || norm.equals("đ") || norm.equals("t");
    }

    private boolean isFalseValue(String s) {
        if (s == null) return false;
        String norm = s.trim().toLowerCase(Locale.ROOT);
        return norm.equals("sai") || norm.equals("false") || norm.equals("s") || norm.equals("f");
    }

    private String normalizeForDedup(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String blankToNull(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        return s.trim();
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private <T> T inNewTransaction(Supplier<T> action) {
        if (transactionManager == null) {
            return action.get();
        }
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> action.get());
    }

    private List<Question> getActiveQuestions(QuestionSet qs) {
        return qs.getQuestions().stream()
                .filter(q -> !STATUS_PENDING_REPLACEMENT.equals(q.getQuestionStatus()))
                .toList();
    }

    public QuestionSetResponse toSetResponse(QuestionSet qs, Integer requestedCount) {
        List<Question> activeQuestions = getActiveQuestions(qs);
        int generatedCount = activeQuestions.size();
        Integer reqCount = requestedCount != null
                ? requestedCount
                : (qs.getGenerationRequest() != null ? qs.getGenerationRequest().getQuestionCount() : null);

        String warning = null;
        if (requestedCount != null && generatedCount < requestedCount) {
            warning = "AI chỉ tạo được " + generatedCount + "/" + requestedCount
                    + " câu hỏi hợp lệ từ nội dung học liệu.";
        }

        return QuestionSetResponse.builder()
                .id(qs.getId())
                .sourceMaterialId(qs.getSourceMaterial() != null ? qs.getSourceMaterial().getId() : null)
                .generationRequestId(qs.getGenerationRequest() != null ? qs.getGenerationRequest().getId() : null)
                .generationStatus(qs.getGenerationRequest() != null ? qs.getGenerationRequest().getStatus() : null)
                .title(qs.getTitle())
                .description(qs.getDescription())
                .setStatus(qs.getSetStatus())
                .questionCount(generatedCount)
                .requestedQuestionCount(reqCount)
                .generatedQuestionCount(generatedCount)
                .warningMessage(warning)
                .savedAt(qs.getSavedAt())
                .questions(activeQuestions.stream().map(this::toQuestionDto).toList())
                .build();
    }

    public QuestionDto toQuestionDto(Question q) {
        String qualityTag = "MANUALLY_REVIEWED".equals(q.getQuestionStatus())
                ? "Đã rà soát thủ công"
                : (q.getReferenceExcerpt() != null && !q.getReferenceExcerpt().isBlank() ? "Độ chính xác cao" : "Cần rà soát");

        return QuestionDto.builder()
                .id(q.getId())
                .questionType(q.getQuestionType())
                .content(q.getContent())
                .explanation(q.getExplanation())
                .difficulty(q.getDifficulty())
                .referenceExcerpt(q.getReferenceExcerpt())
                .referencePage(q.getReferencePage())
                .displayOrder(q.getDisplayOrder())
                .questionStatus(q.getQuestionStatus())
                .qualityTag(qualityTag)
                .options(q.getOptions().stream().map(this::toOptionDto).toList())
                .build();
    }

    public OptionDto toOptionDto(QuestionOption o) {
        return OptionDto.builder()
                .id(o.getId())
                .optionLabel(o.getOptionLabel())
                .content(o.getContent())
                .isCorrect(o.getIsCorrect())
                .displayOrder(o.getDisplayOrder())
                .build();
    }
}
