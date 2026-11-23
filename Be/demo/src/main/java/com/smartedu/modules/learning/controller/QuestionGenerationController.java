package com.smartedu.modules.learning.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.learning.dto.request.CreateQuestionRequest;
import com.smartedu.modules.learning.dto.response.QuestionGenerationResponse;
import com.smartedu.modules.learning.service.QuestionGenerationRequestService;

@RestController
@RequestMapping("/api/question-generation-requests")
public class QuestionGenerationController {

    private final QuestionGenerationRequestService service;

    public QuestionGenerationController(
        QuestionGenerationRequestService service
    ) {
        this.service = service;
    }

    @PostMapping
    public ApiResponse<QuestionGenerationResponse> create(
        Authentication authentication,
        @Valid @RequestBody CreateQuestionRequest request
    ) {
        return ApiResponse.success(
            service.createRequest(
                authentication.getName(), request
            ),
            "Thiết lập yêu cầu tạo câu hỏi thành công"
        );
    }
}
