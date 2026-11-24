package com.smartedu.modules.ai_integration.dto;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class AiDtos {

    @Data @Builder
    public static class MaterialResponse {
        private Long id;
        private String title;
        private String originalFileName;
        private String fileExtension;
        private Long fileSizeBytes;
        private String processingStatus;
        private Long pageCount;
        private LocalDateTime uploadedAt;
    }

    @Data @Builder
    public static class MaterialContentResponse {
        private Long materialId;
        private String title;
        private Long pageCount;
        private String extractionStatus;
        private String extractedText;
        private List<String> chunks;
    }

    @Data
    public static class GenerateQuestionsRequest {
        @NotNull(message = "Vui lòng chọn học liệu")
        private Long learningMaterialId;

        private String contentScope = "FULL_DOCUMENT";
        private String scopeDescription;
        private String questionType = "MULTIPLE_CHOICE";

        @NotNull
        @Min(value = 5, message = "BR-AI-03: Số lượng câu hỏi tối thiểu là 5 câu")
        @Max(value = 20, message = "BR-AI-03: Số lượng câu hỏi tối đa là 20 câu/lần")
        private Integer questionCount = 10;

        private String difficulty = "MIXED";
        private String setTitle;
    }

    @Data
    public static class UpdateQuestionRequest {
        @NotBlank
        private String content;
        private String explanation;
        private String difficulty;
        private List<OptionDto> options;
    }

    @Data @Builder
    public static class OptionDto {
        private Long id;
        private String optionLabel;
        private String content;
        private Boolean isCorrect;
        private Integer displayOrder;
    }

    @Data @Builder
    public static class QuestionDto {
        private Long id;
        private String questionType;
        private String content;
        private String explanation;
        private String difficulty;
        private String referenceExcerpt;
        private Long referencePage;
        private Integer displayOrder;
        private String questionStatus;
        private String qualityTag;
        private List<OptionDto> options;
    }

    @Data @Builder
    public static class QuestionSetResponse {
        private Long id;
        private Long sourceMaterialId;
        private String title;
        private String description;
        private String setStatus;
        private LocalDateTime savedAt;
        private List<QuestionDto> questions;
    }

    @Data
    public static class SaveQuestionSetRequest {
        @NotBlank(message = "Vui lòng nhập tên bộ câu hỏi")
        private String title;
        private String description;
    }

    @Data
    public static class CreateQuizRequest {
        @NotNull
        private Long questionSetId;
        private String title;
        @Min(1) @Max(180)
        private Integer durationMinutes = 30;
        private Boolean shuffleQuestions = true;
        private Boolean shuffleOptions = true;
    }

    @Data
    public static class SubmitQuizRequest {
        private List<AnswerSubmission> answers;
    }

    @Data
    public static class AnswerSubmission {
        @NotNull
        private Long quizTestQuestionId;
        private Long selectedOptionId;
        private String answerText;
    }

    @Data @Builder
    public static class QuizAttemptResultResponse {
        private Long attemptId;
        private Long quizTestId;
        private String quizTitle;
        private String attemptStatus;
        private LocalDateTime startedAt;
        private LocalDateTime submittedAt;
        private Long durationSeconds;
        private BigDecimal score100;
        private BigDecimal score10;
        private Integer correctCount;
        private Integer incorrectCount;
        private Integer unansweredCount;
        private List<AttemptAnswerDetail> details;
    }

    @Data @Builder
    public static class AttemptAnswerDetail {
        private Long quizTestQuestionId;
        private Long questionId;
        private String questionContent;
        private Long selectedOptionId;
        private String selectedOptionText;
        private String correctAnswerText;
        private Boolean isCorrect;
        private String explanation;
        private String referenceExcerpt;
    }
}
