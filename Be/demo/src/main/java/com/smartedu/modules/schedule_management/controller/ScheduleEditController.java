package com.smartedu.modules.schedule_management.controller;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.schedule_management.dto.ScheduleEditDtos;
import com.smartedu.modules.schedule_management.entity.ScheduleOption;
import com.smartedu.modules.schedule_management.service.ScheduleEditService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleEditController {

    private final ScheduleEditService editService;

    @PostMapping("/compare")
    public ApiResponse<ScheduleEditDtos.CompareResponse> compare(
            @Valid @RequestBody ScheduleEditDtos.CompareRequest request
    ) {
        return ApiResponse.success(editService.compare(request));
    }

    @PostMapping("/options/{optionId}/replace-section")
    public ApiResponse<Long> replaceSection(
            @PathVariable Long optionId,
            @Valid @RequestBody ScheduleEditDtos.ReplaceSectionRequest request
    ) {
        ScheduleOption result = editService.replaceSection(optionId, request);

        return ApiResponse.success(
                result.getId(),
                "Đã tạo phương án thời khóa biểu chỉnh sửa."
        );
    }
}
