package com.smartedu.modules.learning.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.learning.service.LearningMaterialService;
import com.smartedu.modules.learning.dto.response.LearningMaterialResponse;

@RestController
@RequestMapping("/api/learning-materials")
public class LearningMaterialController {

    private final LearningMaterialService materialService;

    public LearningMaterialController(
        LearningMaterialService materialService
    ) {
        this.materialService = materialService;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ApiResponse<LearningMaterialResponse> upload(
        Authentication authentication,
        @RequestParam("title") String title,
        @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(
            materialService.upload(
                authentication.getName(),
                title,
                file
            ),
            "Tải học liệu thành công"
        );
    }

    @GetMapping
    public ApiResponse<List<LearningMaterialResponse>> getMyMaterials(
        Authentication authentication
    ) {
        return ApiResponse.success(
            materialService.getMyMaterials(
                authentication.getName()
            )
        );
    }
    
}
