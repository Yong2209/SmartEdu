package com.smartedu.modules.course_registration.controller;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.AddPlanCourseRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreatePlanRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.RegistrationPlanResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.UpdatePlanCoursesRequest;
import com.smartedu.modules.course_registration.service.RegistrationPlanService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/registration/plans")
@RequiredArgsConstructor
public class RegistrationPlanController {

    private final RegistrationPlanService planService;
    private final CurrentUserResolver currentUserResolver;

    @PostMapping
    public ApiResponse<RegistrationPlanResponse> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        return ApiResponse.success(
                planService.createPlan(currentUserResolver.requireCurrentUser(), request),
                "Đã tạo kế hoạch đăng ký học phần."
        );
    }

    @GetMapping
    public ApiResponse<List<RegistrationPlanResponse>> listMyPlans(
            @RequestParam(required = false) Long semesterId) {
        return ApiResponse.success(planService.listMyPlans(currentUserResolver.requireCurrentUser(), semesterId));
    }

    @GetMapping("/{planId}")
    public ApiResponse<RegistrationPlanResponse> getPlanDetail(@PathVariable Long planId) {
        return ApiResponse.success(planService.getPlanDetail(currentUserResolver.requireCurrentUser(), planId));
    }

    @PutMapping("/{planId}/courses")
    public ApiResponse<RegistrationPlanResponse> replacePlanCourses(
            @PathVariable Long planId,
            @Valid @RequestBody UpdatePlanCoursesRequest request) {
        return ApiResponse.success(
                planService.replacePlanCourses(
                        currentUserResolver.requireCurrentUser(), planId, request.getCourseIds()),
                "Đã cập nhật danh sách học phần trong kế hoạch."
        );
    }

    @PostMapping("/{planId}/courses")
    public ApiResponse<RegistrationPlanResponse> addCourseToPlan(
            @PathVariable Long planId,
            @Valid @RequestBody AddPlanCourseRequest request) {
        return ApiResponse.success(
                planService.addCourseToPlan(currentUserResolver.requireCurrentUser(), planId, request.getCourseId()),
                "Đã thêm học phần vào kế hoạch."
        );
    }

    @DeleteMapping("/{planId}/courses/{courseId}")
    public ApiResponse<RegistrationPlanResponse> removeCourseFromPlan(
            @PathVariable Long planId,
            @PathVariable Long courseId) {
        return ApiResponse.success(
                planService.removeCourseFromPlan(currentUserResolver.requireCurrentUser(), planId, courseId),
                "Đã xóa học phần khỏi kế hoạch."
        );
    }
}
