package com.smartedu.modules.ai_integration.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.ai_integration.dto.AiDtos.ConfirmRegenerateRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.CreateQuizRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.CreateReviewSessionRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.GenerateQuestionsRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.GenerationStatusResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.QuestionDto;
import com.smartedu.modules.ai_integration.dto.AiDtos.QuestionSetResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.QuizAttemptResultResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.RegenerateQuestionRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.ReviewAnswerResultResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.ReviewSessionResponse;
import com.smartedu.modules.ai_integration.dto.AiDtos.SaveQuestionSetRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.SubmitQuizRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.SubmitReviewAnswerRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.UpdateQuestionRequest;
import com.smartedu.modules.ai_integration.dto.AiDtos.WrongQuestionDto;
import com.smartedu.modules.ai_integration.service.AiQuestionService;
import com.smartedu.modules.ai_integration.service.QuizService;
import com.smartedu.modules.user_management.entity.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiLearningController {

    private final AiQuestionService aiQuestionService;
    private final QuizService quizService;
    private final CurrentUserResolver currentUserResolver;

    private User getCurrentUser() {
        return currentUserResolver.requireCurrentUser();
    }

    // PB11: Generate questions + check status + retry
    @PostMapping("/questions/generate")
    public ApiResponse<QuestionSetResponse> generateQuestions(@Valid @RequestBody GenerateQuestionsRequest req) {
        QuestionSetResponse response = aiQuestionService.generateQuestions(getCurrentUser(), req);
        String msg = response.getWarningMessage() != null
                ? response.getWarningMessage()
                : "Đã sinh bộ câu hỏi ôn tập thành công.";
        return ApiResponse.success(response, msg);
    }

    @GetMapping("/generation-requests/{requestId}")
    public ApiResponse<GenerationStatusResponse> getGenerationStatus(@PathVariable Long requestId) {
        return ApiResponse.success(aiQuestionService.getGenerationRequestStatus(getCurrentUser(), requestId));
    }

    @PostMapping("/generation-requests/{requestId}/retry")
    public ApiResponse<QuestionSetResponse> retryGeneration(@PathVariable Long requestId) {
        QuestionSetResponse response = aiQuestionService.retryGenerationRequest(getCurrentUser(), requestId);
        String msg = response.getWarningMessage() != null
                ? response.getWarningMessage()
                : "Đã tạo lại bộ câu hỏi thành công.";
        return ApiResponse.success(response, msg);
    }

    // PB12: View single question & question sets
    @GetMapping("/questions/{questionId}")
    public ApiResponse<QuestionDto> getQuestionDetail(@PathVariable Long questionId) {
        return ApiResponse.success(aiQuestionService.getQuestionDetail(getCurrentUser(), questionId));
    }

    @GetMapping("/question-sets")
    public ApiResponse<List<QuestionSetResponse>> listQuestionSets(
            @RequestParam(required = false) String status) {
        return ApiResponse.success(aiQuestionService.listMyQuestionSets(getCurrentUser(), status));
    }

    @GetMapping("/question-sets/{setId}")
    public ApiResponse<QuestionSetResponse> getQuestionSet(@PathVariable Long setId) {
        return ApiResponse.success(aiQuestionService.getQuestionSetDetail(getCurrentUser(), setId));
    }

    // PB13: Edit AI question
    @PutMapping("/questions/{questionId}")
    public ApiResponse<QuestionDto> updateQuestion(@PathVariable Long questionId,
                                                   @Valid @RequestBody UpdateQuestionRequest req) {
        return ApiResponse.success(
                aiQuestionService.updateQuestion(getCurrentUser(), questionId, req),
                "Đã cập nhật câu hỏi."
        );
    }

    // PB14: Regenerate single question with preview / confirm / reject
    @PostMapping("/questions/{questionId}/regenerate")
    public ApiResponse<?> regenerateQuestion(@PathVariable Long questionId,
                                             @RequestBody(required = false) RegenerateQuestionRequest req) {
        Object result = aiQuestionService.regenerateQuestion(getCurrentUser(), questionId, req);
        String msg = (req != null && Boolean.TRUE.equals(req.getConfirmImmediately()))
                ? "Đã sinh lại câu hỏi mới."
                : "Đã tạo câu hỏi thay thế để xem trước. Vui lòng xác nhận hoặc từ chối.";
        return ApiResponse.success(result, msg);
    }

    @PostMapping("/questions/{questionId}/regenerate/confirm")
    public ApiResponse<QuestionDto> confirmRegenerate(@PathVariable Long questionId,
                                                      @Valid @RequestBody ConfirmRegenerateRequest req) {
        return ApiResponse.success(
                aiQuestionService.confirmRegenerate(getCurrentUser(), questionId, req.getPreviewQuestionId()),
                "Đã thay thế câu hỏi thành công."
        );
    }

    @PostMapping("/questions/{questionId}/regenerate/reject")
    public ApiResponse<QuestionDto> rejectRegenerate(@PathVariable Long questionId,
                                                     @RequestParam(required = false) Long previewQuestionId) {
        return ApiResponse.success(
                aiQuestionService.rejectRegenerate(getCurrentUser(), questionId, previewQuestionId),
                "Đã hủy kết quả tạo lại và giữ nguyên câu hỏi cũ."
        );
    }

    // PB15: Save personal question set
    @PostMapping("/question-sets/{setId}/save")
    public ApiResponse<QuestionSetResponse> saveQuestionSet(@PathVariable Long setId,
                                                            @Valid @RequestBody SaveQuestionSetRequest req) {
        return ApiResponse.success(
                aiQuestionService.saveQuestionSet(getCurrentUser(), setId, req),
                "Đã lưu bộ câu hỏi vào thư viện."
        );
    }

    // PB16: Create & start practice quiz
    @PostMapping("/quizzes/start")
    public ApiResponse<?> startQuiz(@Valid @RequestBody CreateQuizRequest req) {
        return ApiResponse.success(
                quizService.createAndStartQuiz(getCurrentUser(), req),
                "Đã khởi tạo phiên làm bài kiểm tra."
        );
    }

    // PB17: Submit & grade quiz
    @PostMapping("/quizzes/attempts/{attemptId}/submit")
    public ApiResponse<QuizAttemptResultResponse> submitQuiz(@PathVariable Long attemptId,
                                                             @RequestBody(required = false) SubmitQuizRequest req) {
        return ApiResponse.success(
                quizService.submitAttempt(getCurrentUser(), attemptId, req),
                "Nộp bài và chấm điểm thành công."
        );
    }

    // PB18: Wrong answers review & retry
    @PostMapping("/quizzes/attempts/{attemptId}/review-wrong")
    public ApiResponse<?> reviewWrongAnswers(@PathVariable Long attemptId) {
        return ApiResponse.success(quizService.createWrongAnswersReviewSession(getCurrentUser(), attemptId));
    }

    @GetMapping("/review/wrong-questions")
    public ApiResponse<List<WrongQuestionDto>> getWrongQuestions(
            @RequestParam(required = false) Long quizTestId,
            @RequestParam(required = false) Long questionSetId,
            @RequestParam(required = false) Long attemptId) {
        return ApiResponse.success(
                quizService.getWrongQuestions(getCurrentUser(), quizTestId, questionSetId, attemptId));
    }

    @PostMapping("/review/sessions")
    public ApiResponse<ReviewSessionResponse> createReviewSession(
            @RequestBody(required = false) CreateReviewSessionRequest req) {
        return ApiResponse.success(
                quizService.createReviewSession(getCurrentUser(), req),
                "Đã khởi tạo phiên ôn tập câu trả lời sai."
        );
    }

    @GetMapping("/review/sessions/{sessionId}")
    public ApiResponse<ReviewSessionResponse> getReviewSession(@PathVariable Long sessionId) {
        return ApiResponse.success(quizService.getReviewSession(getCurrentUser(), sessionId));
    }

    @PostMapping("/review/sessions/{sessionId}/items/{itemId}/answer")
    public ApiResponse<ReviewAnswerResultResponse> submitReviewAnswer(
            @PathVariable Long sessionId,
            @PathVariable Long itemId,
            @RequestBody SubmitReviewAnswerRequest req) {
        return ApiResponse.success(
                quizService.submitReviewAnswer(getCurrentUser(), sessionId, itemId, req),
                "Đã ghi nhận câu trả lời ôn tập."
        );
    }

    // PB19: Quiz history & attempt detail
    @GetMapping("/quizzes/history")
    public ApiResponse<Map<String, Object>> getQuizHistory(
            @RequestParam(defaultValue = "date") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ApiResponse.success(quizService.getMyQuizHistory(getCurrentUser(), sortBy, sortDir));
    }

    @GetMapping("/quizzes/attempts/{attemptId}")
    public ApiResponse<QuizAttemptResultResponse> getAttemptDetail(@PathVariable Long attemptId) {
        return ApiResponse.success(quizService.getAttemptDetail(getCurrentUser(), attemptId));
    }
}
