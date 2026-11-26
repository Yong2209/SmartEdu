package com.smartedu.modules.schedule_management.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.schedule_management.dto.ScheduleEvaluationDtos;
import com.smartedu.modules.schedule_management.service.ScheduleEvaluationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleEvaluationController {

    private final ScheduleEvaluationService evaluationService;

    @GetMapping("/options/{optionId}/evaluation")
    public ApiResponse<ScheduleEvaluationDtos.EvaluationResponse> getEvaluation(
            @PathVariable Long optionId
    ) {
        return ApiResponse.success(
                evaluationService.getEvaluation(optionId)
        );
    }
}
