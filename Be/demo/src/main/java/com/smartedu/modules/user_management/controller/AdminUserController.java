package com.smartedu.modules.user_management.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.user_management.dto.admin.AdminUserDtos;
import com.smartedu.modules.user_management.service.AdminUserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;

    @GetMapping
    public ApiResponse<List<AdminUserDtos.UserResponse>> getUsers(
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(adminUserService.getUsers(keyword));
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<AdminUserDtos.UserResponse> changeRole(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserDtos.ChangeRoleRequest request
    ) {
        return ApiResponse.success(adminUserService.changeRole(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<AdminUserDtos.UserResponse> changeStatus(
            @PathVariable Long id,
            @Valid @RequestBody AdminUserDtos.AccountStatusRequest request
    ) {
        return ApiResponse.success(adminUserService.changeStatus(id, request));
    }
}
