package com.smartedu.modules.ai_integration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AiDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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

        private Long generationRequestId;

        private String contentScope = "FULL_DOCUMENT";
        private String scopeDescription;
        private String questionType = "MULTIPLE_CHOICE";

        @NotNull(message = "Vui lòng nhập số lượng câu hỏi")
        @Min(value = 1, message = "Số lượng câu hỏi tối thiểu là 1 câu")
        @Max(value = 20, message = "Số lượng câu hỏi tối đa là 20 câu/lần")
        private Integer questionCount = 10;

        private String difficulty = "MIXED";
        private String setTitle;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GenerationStatusResponse {
        private Long requestId;
        private Long learningMaterialId;
        private String contentScope;
        private String scopeDescription;
        private String questionType;
        private Integer questionCount;
        private String difficulty;
        private String status;
        private LocalDateTime requestedAt;
        private LocalDateTime completedAt;
        private String errorMessage;
        private Long questionSetId;
    }

    @Data
    public static class UpdateQuestionRequest {
        @NotBlank(message = "Nội dung câu hỏi không được để trống")
        private String content;
        private String explanation;
        private String difficulty;
        private String correctAnswer;
        @Valid
        private List<OptionDto> options;
    }

    @Data
    public static class RegenerateQuestionRequest {
        private String difficulty;
        private String additionalRequirement;
        private Boolean confirmImmediately = false;
    }

    @Data
    public static class ConfirmRegenerateRequest {
        @NotNull(message = "Thiếu mã câu hỏi xem trước")
        private Long previewQuestionId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegeneratePreviewResponse {
        private Long originalQuestionId;
        private Long previewQuestionId;
        private Long questionSetId;
        private QuestionDto originalQuestion;
        private QuestionDto previewQuestion;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionDto {
        private Long id;
        private String optionLabel;
        private String content;
        private Boolean isCorrect;
        private Integer displayOrder;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuestionSetResponse {
        private Long id;
        private Long sourceMaterialId;
        private Long generationRequestId;
        private String generationStatus;
        private String title;
        private String description;
        private String setStatus;
        private Integer questionCount;
        private Integer requestedQuestionCount;
        private Integer generatedQuestionCount;
        private String warningMessage;
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
        @NotNull(message = "Vui lòng chọn bộ câu hỏi")
        private Long questionSetId;
        private String title;
        @NotNull(message = "Vui lòng thiết lập thời gian làm bài")
        @Min(value = 1, message = "Thời gian làm bài tối thiểu là 1 phút")
        @Max(value = 180, message = "Thời gian làm bài tối đa là 180 phút")
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
        @NotNull(message = "Thiếu mã câu hỏi trong bài kiểm tra")
        private Long quizTestQuestionId;
        private Long selectedOptionId;
        private String answerText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QuizAttemptResultResponse {
        private Long attemptId;
        private Long quizTestId;
        private Long questionSetId;
        private String questionSetTitle;
        private String quizTitle;
        private String attemptStatus;
        private Boolean timedOut;
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

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptAnswerDetail {
        private Long quizTestQuestionId;
        private Long questionId;
        private String questionType;
        private String questionContent;
        private Long selectedOptionId;
        private String selectedOptionText;
        private String correctAnswerText;
        private Boolean isCorrect;
        private BigDecimal awardedScore;
        private BigDecimal maxScore;
        private String explanation;
        private String referenceExcerpt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WrongQuestionDto {
        private Long sourceAttemptAnswerId;
        private Long attemptId;
        private Long quizTestId;
        private String quizTitle;
        private Long questionSetId;
        private String questionSetTitle;
        private Long questionId;
        private String questionType;
        private String questionContent;
        private String yourAnswer;
        private String correctAnswer;
        private String explanation;
        private String referenceExcerpt;
        private LocalDateTime submittedAt;
        private List<OptionDto> options;
    }

    @Data
    public static class CreateReviewSessionRequest {
        private Long attemptId;
        private Long quizTestId;
        private Long questionSetId;
        private List<Long> questionIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewSessionResponse {
        private Long reviewSessionId;
        private Long sourceAttemptId;
        private Long sourceQuestionSetId;
        private String sessionStatus;
        private LocalDateTime startedAt;
        private LocalDateTime endedAt;
        private Integer totalItems;
        private Integer masteredCount;
        private List<ReviewItemDto> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewItemDto {
        private Long reviewSessionItemId;
        private Long questionId;
        private String questionType;
        private String questionContent;
        private String reviewStatus;
        private Boolean lastIsCorrect;
        private LocalDateTime lastReviewedAt;
        private String yourPreviousAnswer;
        private String correctAnswer;
        private String explanation;
        private String referenceExcerpt;
        private List<OptionDto> options;
    }

    @Data
    public static class SubmitReviewAnswerRequest {
        private Long selectedOptionId;
        private String answerText;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewAnswerResultResponse {
        private Long reviewSessionId;
        private Long reviewSessionItemId;
        private Long questionId;
        private Boolean isCorrect;
        private String reviewStatus;
        private String sessionStatus;
        private String selectedAnswerText;
        private String correctAnswer;
        private String explanation;
        private String referenceExcerpt;
    }
}
