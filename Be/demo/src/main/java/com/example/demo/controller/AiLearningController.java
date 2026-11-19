package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.AiDtos.*;
import com.example.demo.entity.User;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.service.AiQuestionService;
import com.example.demo.service.MaterialService;
import com.example.demo.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiLearningController {

    private final MaterialService materialService;
    private final AiQuestionService aiQuestionService;
    private final QuizService quizService;
    private final AuthContextHelper authHelper;

    // US08 & US09: Upload học liệu (.pdf/.docx <= 20MB) & Trích xuất văn bản
    @PostMapping(value = "/materials/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<MaterialResponse> uploadMaterial(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title) {
        User user = authHelper.getCurrentUser();
        return ApiResponse.ok("Tải lên và trích xuất học liệu thành công.", materialService.uploadAndExtract(user, file, title));
    }

    @GetMapping("/materials")
    public ApiResponse<List<MaterialResponse>> listMaterials() {
        return ApiResponse.ok(materialService.listUserMaterials(authHelper.getCurrentUser()));
    }

    @GetMapping("/materials/{id}/content")
    public ApiResponse<MaterialContentResponse> getMaterialContent(@PathVariable Long id) {
        return ApiResponse.ok(materialService.getMaterialContent(authHelper.getCurrentUser(), id));
    }

    // US10 - US12: Tạo bộ câu hỏi ôn tập bằng AI
    @PostMapping("/questions/generate")
    public ApiResponse<QuestionSetResponse> generateQuestions(@Valid @RequestBody GenerateQuestionsRequest req) {
        User user = authHelper.getCurrentUser();
        return ApiResponse.ok("Đã sinh bộ câu hỏi ôn tập thành công.", aiQuestionService.generateQuestions(user, req));
    }

    // US13: Chỉnh sửa câu hỏi
    @PutMapping("/questions/{questionId}")
    public ApiResponse<QuestionDto> updateQuestion(@PathVariable Long questionId,
                                                   @Valid @RequestBody UpdateQuestionRequest req) {
        return ApiResponse.ok("Đã cập nhật câu hỏi.", aiQuestionService.updateQuestion(authHelper.getCurrentUser(), questionId, req));
    }

    // US14: Yêu cầu AI tạo lại 1 câu hỏi đơn lẻ
    @PostMapping("/questions/{questionId}/regenerate")
    public ApiResponse<QuestionDto> regenerateQuestion(@PathVariable Long questionId) {
        return ApiResponse.ok("Đã sinh lại câu hỏi mới.", aiQuestionService.regenerateSingleQuestion(authHelper.getCurrentUser(), questionId));
    }

    // US15: Lưu & quản lý bộ câu hỏi cá nhân
    @PostMapping("/question-sets/{setId}/save")
    public ApiResponse<QuestionSetResponse> saveQuestionSet(@PathVariable Long setId,
                                                            @Valid @RequestBody SaveQuestionSetRequest req) {
        return ApiResponse.ok("Đã lưu bộ câu hỏi vào thư viện.", aiQuestionService.saveQuestionSet(authHelper.getCurrentUser(), setId, req));
    }

    @GetMapping("/question-sets")
    public ApiResponse<List<QuestionSetResponse>> listQuestionSets() {
        return ApiResponse.ok(aiQuestionService.listMyQuestionSets(authHelper.getCurrentUser()));
    }

    @GetMapping("/question-sets/{setId}")
    public ApiResponse<QuestionSetResponse> getQuestionSet(@PathVariable Long setId) {
        return ApiResponse.ok(aiQuestionService.getQuestionSetDetail(authHelper.getCurrentUser(), setId));
    }

    // US16: Bắt đầu làm bài kiểm tra trực tuyến
    @PostMapping("/quizzes/start")
    public ApiResponse<Map<String, Object>> startQuiz(@Valid @RequestBody CreateQuizRequest req) {
        return ApiResponse.ok("Đã khởi tạo phiên làm bài kiểm tra.", quizService.createAndStartQuiz(authHelper.getCurrentUser(), req));
    }

    // US17: Nộp bài và chấm điểm tự động
    @PostMapping("/quizzes/attempts/{attemptId}/submit")
    public ApiResponse<QuizAttemptResultResponse> submitQuiz(@PathVariable Long attemptId,
                                                             @RequestBody SubmitQuizRequest req) {
        return ApiResponse.ok("Nộp bài và chấm điểm thành công.", quizService.submitAttempt(authHelper.getCurrentUser(), attemptId, req));
    }

    // US18: Tạo phiên ôn tập các câu sai
    @PostMapping("/quizzes/attempts/{attemptId}/review-wrong")
    public ApiResponse<Map<String, Object>> reviewWrongAnswers(@PathVariable Long attemptId) {
        return ApiResponse.ok(quizService.createWrongAnswersReviewSession(authHelper.getCurrentUser(), attemptId));
    }

    // US19: Xem lịch sử làm bài & chi tiết bài làm
    @GetMapping("/quizzes/history")
    public ApiResponse<Map<String, Object>> getQuizHistory() {
        return ApiResponse.ok(quizService.getMyQuizHistory(authHelper.getCurrentUser()));
    }

    @GetMapping("/quizzes/attempts/{attemptId}")
    public ApiResponse<QuizAttemptResultResponse> getAttemptDetail(@PathVariable Long attemptId) {
        return ApiResponse.ok(quizService.getAttemptDetail(authHelper.getCurrentUser(), attemptId));
    }
}
