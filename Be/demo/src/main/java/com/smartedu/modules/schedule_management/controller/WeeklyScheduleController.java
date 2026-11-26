package com.smartedu.modules.schedule_management.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.schedule_management.dto.WeeklyScheduleDtos;
import com.smartedu.modules.schedule_management.service.WeeklyScheduleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class WeeklyScheduleController {

    private final WeeklyScheduleService weeklyScheduleService;

    @GetMapping("/options/{optionId}/weekly")
    public ApiResponse<WeeklyScheduleDtos.WeeklyScheduleResponse> getWeekly(
            @PathVariable Long optionId
    ) {
        return ApiResponse.success(
                weeklyScheduleService.getWeeklySchedule(optionId)
        );
    }
}
