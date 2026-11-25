package com.smartedu.modules.authentication.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(max = 150) String fullName,
        @NotBlank @Email @Size(max = 255) String email,
        @Size(max = 20) String phone,
        @Size(max = 20) String gender,
        @Size(max = 500) String address,
        @NotBlank @Size(min = 8, max = 72) String password
) {
}
