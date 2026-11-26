package com.smartedu.modules.academic_management.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.academic_management.dto.PrerequisiteDtos;
import com.smartedu.modules.academic_management.service.PrerequisiteManagementService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/prerequisites")
@RequiredArgsConstructor
public class PrerequisiteManagementController {

    private final PrerequisiteManagementService service;

    @PostMapping
    public ApiResponse<PrerequisiteDtos.PrerequisiteResponse> create(
            @Valid @RequestBody PrerequisiteDtos.CreateRequest request
    ) {
        return ApiResponse.success(service.create(request));
    }

    @GetMapping("/courses/{courseId}")
    public ApiResponse<List<PrerequisiteDtos.PrerequisiteResponse>> getByCourse(
            @PathVariable Long courseId
    ) {
        return ApiResponse.success(service.getByCourse(courseId));
    }

    @DeleteMapping("/courses/{courseId}/{prerequisiteCourseId}")
    public ApiResponse<Void> delete(
            @PathVariable Long courseId,
            @PathVariable Long prerequisiteCourseId
    ) {
        service.delete(courseId, prerequisiteCourseId);

        return ApiResponse.success(
                null,
                "Đã xóa điều kiện tiên quyết."
        );
    }
}
