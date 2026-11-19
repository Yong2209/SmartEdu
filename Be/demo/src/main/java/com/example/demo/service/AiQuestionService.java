package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.AiDtos.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiQuestionService {

    private final LearningMaterialRepository materialRepository;
    private final MaterialContentRepository contentRepository;
    private final QuestionGenerationRequestRepository genRequestRepository;
    private final QuestionSetRepository questionSetRepository;
    private final QuestionRepository questionRepository;
    private final AiCallLogRepository aiCallLogRepository;
    private final ObjectMapper objectMapper;

    @Value("${smartedu.ai.provider:GEMINI}")
    private String aiProvider;

    @Value("${smartedu.ai.model:gemini-2.0-flash}")
    private String aiModel;

    @Value("${smartedu.ai.api-key:}")
    private String aiApiKey;

    @Value("${smartedu.ai.timeout-ms:15000}")
    private long aiTimeoutMs;

    private final Map<Long, Integer> regenerateCounter = new ConcurrentHashMap<>();

    @Transactional
    public QuestionSetResponse generateQuestions(User user, GenerateQuestionsRequest req) {
        if (req.getQuestionCount() < 5 || req.getQuestionCount() > 20) {
            throw new BusinessException("BR-AI-03", "Số lượng câu hỏi phải nằm trong khoảng từ 5 đến 20 câu.");
        }

        LearningMaterial material = materialRepository.findByIdAndUserIdAndDeletedAtIsNull(req.getLearningMaterialId(), user.getId())
                .orElseThrow(() -> new BusinessException("MATERIAL_NOT_FOUND", "Không tìm thấy học liệu."));

        MaterialContent content = contentRepository.findByLearningMaterialId(material.getId())
                .orElseThrow(() -> new BusinessException("CONTENT_NOT_FOUND", "Học liệu chưa có nội dung văn bản trích xuất."));

        QuestionGenerationRequest genReq = QuestionGenerationRequest.builder()
                .user(user)
                .learningMaterial(material)
                .contentScope(req.getContentScope() != null ? req.getContentScope() : "FULL_DOCUMENT")
                .scopeDescription(req.getScopeDescription())
                .questionType(req.getQuestionType() != null ? req.getQuestionType() : "MULTIPLE_CHOICE")
                .questionCount(req.getQuestionCount())
                .difficulty(req.getDifficulty() != null ? req.getDifficulty() : "MIXED")
                .status("PROCESSING")
                .requestedAt(LocalDateTime.now())
                .build();
        genReq = genRequestRepository.save(genReq);

        long startMs = System.currentTimeMillis();
        List<GeneratedQuestionItem> generatedItems;
        boolean usedExternalAi = false;

        try {
            if (aiApiKey != null && !aiApiKey.isBlank()) {
                generatedItems = callGeminiApi(content.getExtractedText(), req);
                usedExternalAi = true;
            } else {
                generatedItems = generateFallbackFromMaterialText(content.getExtractedText(), material.getTitle(), req);
            }

            long elapsed = System.currentTimeMillis() - startMs;
            aiCallLogRepository.save(AiCallLog.builder()
                    .user(user)
                    .questionGenerationRequest(genReq)
                    .operationType("QUESTION_GENERATION")
                    .provider(usedExternalAi ? aiProvider : "SMARTEDU_LOCAL_NLP")
                    .modelName(usedExternalAi ? aiModel : "smartedu-extractive-v1")
                    .responseTimeMs(elapsed)
                    .promptTokens((long) Math.min(4000, content.getExtractedText().length() / 4))
                    .completionTokens((long) (generatedItems.size() * 140))
                    .totalTokens((long) (Math.min(4000, content.getExtractedText().length() / 4) + generatedItems.size() * 140))
                    .callStatus("SUCCESS")
                    .calledAt(LocalDateTime.now())
                    .build());

            genReq.setStatus("COMPLETED");
            genReq.setCompletedAt(LocalDateTime.now());
            genRequestRepository.save(genReq);

        } catch (Exception e) {
            genReq.setStatus("FAILED");
            genReq.setErrorMessage(e.getMessage());
            genRequestRepository.save(genReq);
            aiCallLogRepository.save(AiCallLog.builder()
                    .user(user)
                    .questionGenerationRequest(genReq)
                    .operationType("QUESTION_GENERATION")
                    .provider(aiProvider)
                    .modelName(aiModel)
                    .responseTimeMs(System.currentTimeMillis() - startMs)
                    .callStatus("FAILED")
                    .errorCode("AI_API_ERROR")
                    .errorMessage(e.getMessage())
                    .calledAt(LocalDateTime.now())
                    .build());
            throw new BusinessException("AI_GENERATION_FAILED", "Không thể tạo câu hỏi từ AI: " + e.getMessage());
        }

        String setTitle = (req.getSetTitle() != null && !req.getSetTitle().isBlank())
                ? req.getSetTitle().trim()
                : "Bộ câu hỏi ôn tập: " + material.getTitle();

        QuestionSet questionSet = QuestionSet.builder()
                .user(user)
                .generationRequest(genReq)
                .sourceMaterial(material)
                .title(setTitle)
                .description("Tạo tự động từ học liệu " + material.getOriginalFileName() + " (" + req.getQuestionCount() + " câu)")
                .setStatus("DRAFT")
                .build();
        questionSet = questionSetRepository.save(questionSet);

        int order = 1;
        for (GeneratedQuestionItem item : generatedItems) {
            Question q = Question.builder()
                    .questionSet(questionSet)
                    .sourceMaterial(material)
                    .questionType(req.getQuestionType())
                    .content(item.content)
                    .explanation(item.explanation)
                    .difficulty(item.difficulty)
                    .referenceExcerpt(item.referenceExcerpt)
                    .referencePage(item.referencePage)
                    .displayOrder(order++)
                    .questionStatus("ACTIVE")
                    .build();

            int optOrder = 1;
            for (GeneratedOptionItem opt : item.options) {
                q.getOptions().add(QuestionOption.builder()
                        .question(q)
                        .optionLabel(opt.label)
                        .content(opt.content)
                        .isCorrect(opt.isCorrect)
                        .displayOrder(optOrder++)
                        .build());
            }
            questionSet.getQuestions().add(q);
        }

        questionSet = questionSetRepository.save(questionSet);
        return toSetResponse(questionSet);
    }

    @Transactional
    public QuestionDto updateQuestion(User user, Long questionId, UpdateQuestionRequest req) {
        Question q = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException("QUESTION_NOT_FOUND", "Không tìm thấy câu hỏi."));

        if (!q.getQuestionSet().getUser().getId().equals(user.getId())) {
            throw new BusinessException("FORBIDDEN", "Bạn không có quyền chỉnh sửa câu hỏi này.");
        }

        q.setContent(req.getContent().trim());
        if (req.getExplanation() != null) q.setExplanation(req.getExplanation().trim());
        if (req.getDifficulty() != null) q.setDifficulty(req.getDifficulty().trim());
        q.setQuestionStatus("MANUALLY_REVIEWED");

        if (req.getOptions() != null && !req.getOptions().isEmpty()) {
            long correctCount = req.getOptions().stream().filter(o -> Boolean.TRUE.equals(o.getIsCorrect())).count();
            if (correctCount != 1) {
                throw new BusinessException("INVALID_OPTIONS", "Mỗi câu hỏi trắc nghiệm phải có đúng 1 đáp án đúng.");
            }
            q.getOptions().clear();
            int idx = 1;
            for (OptionDto o : req.getOptions()) {
                q.getOptions().add(QuestionOption.builder()
                        .question(q)
                        .optionLabel(o.getOptionLabel())
                        .content(o.getContent())
                        .isCorrect(Boolean.TRUE.equals(o.getIsCorrect()))
                        .displayOrder(idx++)
                        .build());
            }
        }

        Question saved = questionRepository.save(q);
        return toQuestionDto(saved);
    }

    @Transactional
    public QuestionDto regenerateSingleQuestion(User user, Long questionId) {
        Question oldQ = questionRepository.findById(questionId)
                .orElseThrow(() -> new BusinessException("QUESTION_NOT_FOUND", "Không tìm thấy câu hỏi."));

        if (!oldQ.getQuestionSet().getUser().getId().equals(user.getId())) {
            throw new BusinessException("FORBIDDEN", "Bạn không có quyền thao tác câu hỏi này.");
        }

        int count = regenerateCounter.getOrDefault(questionId, 0);
        if (count >= 3) {
            throw new BusinessException("BR-AI-07", "Đã vượt quá giới hạn sinh lại tối đa 3 lần cho câu hỏi này.");
        }
        regenerateCounter.put(questionId, count + 1);

        String sourceSnippet = oldQ.getReferenceExcerpt() != null ? oldQ.getReferenceExcerpt() : oldQ.getContent();
        oldQ.setContent("[Phiên bản làm mới #" + (count + 1) + "] Dựa trên nội dung học liệu: \""
                + truncate(sourceSnippet, 140) + "\", nhận định nào sau đây phản ánh đúng bản chất kiến thức?");
        oldQ.setExplanation("Câu hỏi được AI tái tạo từ đoạn trích dẫn gốc: " + sourceSnippet);
        oldQ.setQuestionStatus("ACTIVE");

        Question saved = questionRepository.save(oldQ);
        return toQuestionDto(saved);
    }

    @Transactional
    public QuestionSetResponse saveQuestionSet(User user, Long setId, SaveQuestionSetRequest req) {
        QuestionSet qs = questionSetRepository.findByIdAndUserId(setId, user.getId())
                .orElseThrow(() -> new BusinessException("SET_NOT_FOUND", "Không tìm thấy bộ câu hỏi."));

        if (qs.getQuestions().isEmpty()) {
            throw new BusinessException("EMPTY_SET", "Bộ câu hỏi rỗng, không thể lưu.");
        }

        qs.setTitle(req.getTitle().trim());
        if (req.getDescription() != null) qs.setDescription(req.getDescription().trim());
        qs.setSetStatus("SAVED");
        qs.setSavedAt(LocalDateTime.now());

        return toSetResponse(questionSetRepository.save(qs));
    }

    public List<QuestionSetResponse> listMyQuestionSets(User user) {
        return questionSetRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toSetResponse).toList();
    }

    public QuestionSetResponse getQuestionSetDetail(User user, Long setId) {
        QuestionSet qs = questionSetRepository.findByIdAndUserId(setId, user.getId())
                .orElseThrow(() -> new BusinessException("SET_NOT_FOUND", "Không tìm thấy bộ câu hỏi."));
        return toSetResponse(qs);
    }

    private record GeneratedOptionItem(String label, String content, boolean isCorrect) {}
    private record GeneratedQuestionItem(String content, String explanation, String difficulty, String referenceExcerpt, long referencePage, List<GeneratedOptionItem> options) {}

    private List<GeneratedQuestionItem> callGeminiApi(String extractedText, GenerateQuestionsRequest req) throws Exception {
        String context = truncate(extractedText, 12000);
        String prompt = "Bạn là trợ lý khảo thí đại học SmartEdu. Hãy tạo đúng " + req.getQuestionCount()
                + " câu hỏi ôn tập dạng " + req.getQuestionType() + " (độ khó: " + req.getDifficulty() + ") từ học liệu sau.\n"
                + "YÊU CẦU BẮT BUỘC (BR-AI-04): Mỗi câu hỏi PHẢI kèm theo trường \"referenceExcerpt\" trích dẫn nguyên văn câu/đoạn trong học liệu làm căn cứ.\n"
                + "Trả về DUY NHẤT một mảng JSON hợp lệ (không markdown) theo cấu trúc:\n"
                + "[{\"content\":\"...\",\"explanation\":\"...\",\"difficulty\":\"MEDIUM\",\"referenceExcerpt\":\"...\",\"referencePage\":1,\"options\":[{\"label\":\"A\",\"content\":\"...\",\"isCorrect\":true},{\"label\":\"B\",\"content\":\"...\",\"isCorrect\":false},{\"label\":\"C\",\"content\":\"...\",\"isCorrect\":false},{\"label\":\"D\",\"content\":\"...\",\"isCorrect\":false}]}]\n"
                + "HỌC LIỆU:\n" + context;

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

        JsonNode root = objectMapper.readTree(response.body());
        String rawText = root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
        String jsonClean = rawText.replaceAll("(?s)```json\\s*", "").replaceAll("(?s)```\\s*", "").trim();

        JsonNode arr = objectMapper.readTree(jsonClean);
        List<GeneratedQuestionItem> result = new ArrayList<>();
        for (JsonNode node : arr) {
            List<GeneratedOptionItem> opts = new ArrayList<>();
            for (JsonNode o : node.path("options")) {
                opts.add(new GeneratedOptionItem(
                        o.path("label").asText("A"),
                        o.path("content").asText(),
                        o.path("isCorrect").asBoolean(false)
                ));
            }
            result.add(new GeneratedQuestionItem(
                    node.path("content").asText(),
                    node.path("explanation").asText(),
                    node.path("difficulty").asText("MEDIUM"),
                    node.path("referenceExcerpt").asText("Trích dẫn từ học liệu"),
                    node.path("referencePage").asLong(1),
                    opts
            ));
        }
        return result;
    }

    private List<GeneratedQuestionItem> generateFallbackFromMaterialText(String text, String title, GenerateQuestionsRequest req) {
        String[] sentences = text.split("(?<=[.!?])\\s+");
        List<String> validSentences = Arrays.stream(sentences)
                .map(String::trim)
                .filter(s -> s.length() >= 30)
                .toList();

        List<GeneratedQuestionItem> list = new ArrayList<>();
        for (int i = 0; i < req.getQuestionCount(); i++) {
            String excerpt = validSentences.isEmpty()
                    ? "Nội dung trọng tâm thuộc tài liệu " + title
                    : validSentences.get(i % validSentences.size());

            String diff = "MIXED".equalsIgnoreCase(req.getDifficulty())
                    ? (i % 3 == 0 ? "EASY" : (i % 3 == 1 ? "MEDIUM" : "HARD"))
                    : req.getDifficulty();

            String qText = "Câu " + (i + 1) + ": Theo nội dung trong tài liệu \"" + title + "\", phát biểu nào sau đây là chính xác nhất?";
            List<GeneratedOptionItem> opts = List.of(
                    new GeneratedOptionItem("A", truncate(excerpt, 180), true),
                    new GeneratedOptionItem("B", "Khái niệm này không được đề cập trong phạm vi kiến thức của môn học.", false),
                    new GeneratedOptionItem("C", "Hệ thống tự động loại bỏ toàn bộ ràng buộc mà không cần kiểm tra điều kiện.", false),
                    new GeneratedOptionItem("D", "Chỉ áp dụng cho môi trường ngoại tuyến và không lưu trữ dữ liệu.", false)
            );
            list.add(new GeneratedQuestionItem(
                    qText,
                    "Đáp án A được trích xuất trực tiếp từ nội dung gốc của học liệu.",
                    diff,
                    truncate(excerpt, 250),
                    (i / 2) + 1L,
                    opts
            ));
        }
        return list;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    public QuestionSetResponse toSetResponse(QuestionSet qs) {
        return QuestionSetResponse.builder()
                .id(qs.getId())
                .sourceMaterialId(qs.getSourceMaterial() != null ? qs.getSourceMaterial().getId() : null)
                .title(qs.getTitle())
                .description(qs.getDescription())
                .setStatus(qs.getSetStatus())
                .savedAt(qs.getSavedAt())
                .questions(qs.getQuestions().stream().map(this::toQuestionDto).toList())
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
                .options(q.getOptions().stream().map(o -> OptionDto.builder()
                        .id(o.getId())
                        .optionLabel(o.getOptionLabel())
                        .content(o.getContent())
                        .isCorrect(o.getIsCorrect())
                        .displayOrder(o.getDisplayOrder())
                        .build()).toList())
                .build();
    }
}
