package com.smartedu.modules.authentication.dto.response;

import java.time.Instant;

public record LoginResponse(
        String accessToken,
        Instant expiresAt,
        Long userId,
        String fullName,
        String username,
        String email,
        String role
) {
}
