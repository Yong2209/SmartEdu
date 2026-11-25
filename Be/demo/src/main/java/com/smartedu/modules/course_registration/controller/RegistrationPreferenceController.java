package com.smartedu.modules.course_registration.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.SchedulePreferenceRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.SchedulePreferenceResponse;
import com.smartedu.modules.course_registration.service.RegistrationPreferenceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/registration/plans/{planId}")
@RequiredArgsConstructor
public class RegistrationPreferenceController {

    private final RegistrationPreferenceService preferenceService;
    private final CurrentUserResolver currentUserResolver;

    // PB24: Credit limits
    @GetMapping("/credit-limits")
    public ApiResponse<CreditLimitResponse> getCreditLimits(@PathVariable Long planId) {
        return ApiResponse.success(
                preferenceService.getCreditLimits(currentUserResolver.requireCurrentUser(), planId));
    }

    @PutMapping("/credit-limits")
    public ApiResponse<CreditLimitResponse> saveCreditLimits(
            @PathVariable Long planId,
            @Valid @RequestBody CreditLimitRequest request) {
        return ApiResponse.success(
                preferenceService.saveCreditLimits(currentUserResolver.requireCurrentUser(), planId, request),
                "Đã lưu giới hạn tín chỉ cho kế hoạch đăng ký."
        );
    }

    // PB25: Schedule preferences (soft constraints)
    @GetMapping("/schedule-preferences")
    public ApiResponse<SchedulePreferenceResponse> getSchedulePreferences(@PathVariable Long planId) {
        return ApiResponse.success(
                preferenceService.getSchedulePreferences(currentUserResolver.requireCurrentUser(), planId));
    }

    @PutMapping("/schedule-preferences")
    public ApiResponse<SchedulePreferenceResponse> saveSchedulePreferences(
            @PathVariable Long planId,
            @Valid @RequestBody SchedulePreferenceRequest request) {
        return ApiResponse.success(
                preferenceService.saveSchedulePreferences(currentUserResolver.requireCurrentUser(), planId, request),
                "Đã lưu sở thích thời gian học."
        );
    }
}
