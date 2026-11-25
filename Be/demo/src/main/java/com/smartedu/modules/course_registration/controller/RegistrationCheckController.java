package com.smartedu.modules.course_registration.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitCheckRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitCheckResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.PrerequisiteCheckRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.PrerequisiteCheckResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ScheduleConflictCheckRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ScheduleConflictCheckResponse;
import com.smartedu.modules.course_registration.service.RegistrationCheckService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/registration/checks")
@RequiredArgsConstructor
public class RegistrationCheckController {

    private final RegistrationCheckService checkService;
    private final CurrentUserResolver currentUserResolver;

    // PB26: Schedule conflict check
    @PostMapping("/schedule-conflicts")
    public ApiResponse<ScheduleConflictCheckResponse> checkScheduleConflicts(
            @RequestBody ScheduleConflictCheckRequest request) {
        return ApiResponse.success(
                checkService.checkScheduleConflicts(currentUserResolver.requireCurrentUser(), request),
                "Đã hoàn tất kiểm tra trùng lịch học phần."
        );
    }

    // PB27: Prerequisite check
    @PostMapping("/prerequisites")
    public ApiResponse<PrerequisiteCheckResponse> checkPrerequisites(
            @RequestBody PrerequisiteCheckRequest request) {
        return ApiResponse.success(
                checkService.checkPrerequisites(currentUserResolver.requireCurrentUser(), request),
                "Đã hoàn tất kiểm tra điều kiện tiên quyết."
        );
    }

    // PB28: Credit limit check
    @PostMapping("/credit-limits")
    public ApiResponse<CreditLimitCheckResponse> checkCreditLimits(
            @RequestBody CreditLimitCheckRequest request) {
        return ApiResponse.success(
                checkService.checkCreditLimits(currentUserResolver.requireCurrentUser(), request),
                "Đã hoàn tất kiểm tra giới hạn tổng tín chỉ."
        );
    }
}
