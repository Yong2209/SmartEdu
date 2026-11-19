package com.smartedu.modules.learning.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.learning.service.MaterialContentService;
import com.smartedu.modules.learning.dto.response.MaterialContentResponse;

@RestController
@RequestMapping("/api/learning-materials")
public class MaterialContentController {

    private final MaterialContentService contentService;

    public MaterialContentController(
        MaterialContentService contentService
    ) {
        this.contentService = contentService;
    }

    @GetMapping("/{id}/content")
    public ApiResponse<MaterialContentResponse> getContent(
        Authentication authentication,
        @PathVariable Long id
    ) {
        return ApiResponse.success(
            contentService.getContent(
                authentication.getName(), id
            )
        );
    }

    @PostMapping("/{id}/extract")
    public ApiResponse<MaterialContentResponse> reExtract(
        Authentication authentication,
        @PathVariable Long id
    ) {
        return ApiResponse.success(
            contentService.reExtract(
                authentication.getName(), id
            ),
            "Trích xuất nội dung thành công"
        );
    }
}
