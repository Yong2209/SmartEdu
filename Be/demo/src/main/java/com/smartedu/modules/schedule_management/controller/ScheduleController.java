package com.smartedu.modules.schedule_management.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.schedule_management.dto.ScheduleDtos;
import com.smartedu.modules.schedule_management.service.ScheduleGenerationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleGenerationService generationService;

    @PostMapping("/generate")
    public ApiResponse<ScheduleDtos.GenerateResponse> generate(
            @Valid @RequestBody ScheduleDtos.GenerateRequest request
    ) {
        return ApiResponse.success(
                generationService.generate(request),
                "Tạo thời khóa biểu thành công."
        );
    }
}
