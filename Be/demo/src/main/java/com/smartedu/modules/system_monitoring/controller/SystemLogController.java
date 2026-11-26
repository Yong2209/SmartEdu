package com.smartedu.modules.system_monitoring.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.system_monitoring.dto.SystemLogDtos;
import com.smartedu.modules.system_monitoring.service.SystemLogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/system-logs")
@RequiredArgsConstructor
public class SystemLogController {

    private final SystemLogService systemLogService;

    @GetMapping("/activities")
    public ApiResponse<List<SystemLogDtos.ActivityLogResponse>> getActivities(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate to
    ) {
        return ApiResponse.success(
                systemLogService.getActivityLogs(from, to)
        );
    }

    @GetMapping("/ai")
    public ApiResponse<List<SystemLogDtos.AiLogResponse>> getAiLogs() {
        return ApiResponse.success(systemLogService.getAiLogs());
    }
}
