package com.smartedu.modules.authentication.dto.response;

public record RegisterResponse(
        Long userId,
        String email,
        String accountStatus,
        String message
) {
}
