package com.smartedu.modules.user_management.dto.admin;

import jakarta.validation.constraints.NotBlank;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record UserResponse(
            Long id,
            String studentCode,
            String username,
            String fullName,
            String email,
            String phone,
            String roleCode,
            String accountStatus,
            String createdAt
    ) {
    }

    public record ChangeRoleRequest(
            @NotBlank String roleCode
    ) {
    }

    public record AccountStatusRequest(
            @NotBlank String status
    ) {
    }
}
