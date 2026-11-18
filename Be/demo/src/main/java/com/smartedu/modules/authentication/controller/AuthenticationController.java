package com.smartedu.modules.authentication.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.authentication.dto.request.RegisterRequest;
import com.smartedu.modules.authentication.dto.request.ResendVerificationRequest;
import com.smartedu.modules.authentication.dto.request.VerifyEmailRequest;
import com.smartedu.modules.authentication.dto.response.RegisterResponse;
import com.smartedu.modules.authentication.service.RegistrationService;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final RegistrationService registrationService;

    public AuthenticationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        RegisterResponse response = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Account registered"));
    }

    @PostMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        registrationService.verifyEmail(request);
        return ApiResponse.success(null, "Email verified successfully");
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request) {
        registrationService.resendVerification(request);
        return ApiResponse.success(null, "Verification OTP sent");
    }
}
