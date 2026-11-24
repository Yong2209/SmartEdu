package com.smartedu.modules.ai_integration.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.ai_integration.dto.AiDtos.*;
import com.smartedu.modules.ai_integration.service.AiQuestionService;
import com.smartedu.modules.ai_integration.service.QuizService;
import com.smartedu.modules.user_management.entity.User;
import com.smartedu.modules.user_management.repository.UserRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiLearningController {

    private final AiQuestionService aiQuestionService;
    private final QuizService quizService;
    private final UserRepository userRepository;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BusinessException("Vui lòng đăng nhập để sử dụng tính năng này.", HttpStatus.UNAUTHORIZED);
        }
        String principalName = auth.getName();
        return userRepository.findByEmail(principalName)
                .or(() -> userRepository.findByUsername(principalName))
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng.", HttpStatus.UNAUTHORIZED));
    }

    @PostMapping("/questions/generate")
    public ApiResponse<QuestionSetResponse> generateQuestions(@Valid @RequestBody GenerateQuestionsRequest req) {
        return ApiResponse.success(
                aiQuestionService.generateQuestions(getCurrentUser(), req),
                "Đã sinh bộ câu hỏi ôn tập thành công."
        );
    }

    @PutMapping("/questions/{questionId}")
    public ApiResponse<QuestionDto> updateQuestion(@PathVariable Long questionId,
                                                   @Valid @RequestBody UpdateQuestionRequest req) {
        return ApiResponse.success(
                aiQuestionService.updateQuestion(getCurrentUser(), questionId, req),
                "Đã cập nhật câu hỏi."
        );
    }

    @PostMapping("/questions/{questionId}/regenerate")
    public ApiResponse<QuestionDto> regenerateQuestion(@PathVariable Long questionId) {
        return ApiResponse.success(
                aiQuestionService.regenerateSingleQuestion(getCurrentUser(), questionId),
                "Đã sinh lại câu hỏi mới."
        );
    }

    @PostMapping("/question-sets/{setId}/save")
    public ApiResponse<QuestionSetResponse> saveQuestionSet(@PathVariable Long setId,
                                                            @Valid @RequestBody SaveQuestionSetRequest req) {
        return ApiResponse.success(
                aiQuestionService.saveQuestionSet(getCurrentUser(), setId, req),
                "Đã lưu bộ câu hỏi vào thư viện."
        );
    }

    @GetMapping("/question-sets")
    public ApiResponse<List<QuestionSetResponse>> listQuestionSets() {
        return ApiResponse.success(aiQuestionService.listMyQuestionSets(getCurrentUser()));
    }

    @GetMapping("/question-sets/{setId}")
    public ApiResponse<QuestionSetResponse> getQuestionSet(@PathVariable Long setId) {
        return ApiResponse.success(aiQuestionService.getQuestionSetDetail(getCurrentUser(), setId));
    }

    @PostMapping("/quizzes/start")
    public ApiResponse<?> startQuiz(@Valid @RequestBody CreateQuizRequest req) {
        return ApiResponse.success(
                quizService.createAndStartQuiz(getCurrentUser(), req),
                "Đã khởi tạo phiên làm bài kiểm tra."
        );
    }

    @PostMapping("/quizzes/attempts/{attemptId}/submit")
    public ApiResponse<QuizAttemptResultResponse> submitQuiz(@PathVariable Long attemptId,
                                                             @RequestBody SubmitQuizRequest req) {
        return ApiResponse.success(
                quizService.submitAttempt(getCurrentUser(), attemptId, req),
                "Nộp bài và chấm điểm thành công."
        );
    }

    @PostMapping("/quizzes/attempts/{attemptId}/review-wrong")
    public ApiResponse<?> reviewWrongAnswers(@PathVariable Long attemptId) {
        return ApiResponse.success(quizService.createWrongAnswersReviewSession(getCurrentUser(), attemptId));
    }

    @GetMapping("/quizzes/history")
    public ApiResponse<?> getQuizHistory() {
        return ApiResponse.success(quizService.getMyQuizHistory(getCurrentUser()));
    }

    @GetMapping("/quizzes/attempts/{attemptId}")
    public ApiResponse<QuizAttemptResultResponse> getAttemptDetail(@PathVariable Long attemptId) {
        return ApiResponse.success(quizService.getAttemptDetail(getCurrentUser(), attemptId));
    }
}