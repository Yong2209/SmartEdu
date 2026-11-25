package com.smartedu.modules.user_management.dto.response;

public record ProfileResponse(
    Long id,
    String studentCode,
    String fullName,
    String username,
    String email,
    String phone,
    String address,
    String gender,
    String avatarUrl
) {}
