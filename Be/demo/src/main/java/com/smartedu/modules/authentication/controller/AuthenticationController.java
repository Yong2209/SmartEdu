package com.smartedu.modules.authentication.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.authentication.dto.request.LoginRequest;
import com.smartedu.modules.authentication.dto.request.ForgotPasswordRequest;
import com.smartedu.modules.authentication.dto.request.RegisterRequest;
import com.smartedu.modules.authentication.dto.request.ResendVerificationRequest;
import com.smartedu.modules.authentication.dto.request.ResetPasswordRequest;
import com.smartedu.modules.authentication.dto.request.VerifyEmailRequest;
import com.smartedu.modules.authentication.dto.response.LoginResponse;
import com.smartedu.modules.authentication.dto.response.RegisterResponse;
import com.smartedu.modules.authentication.service.AuthenticationService;
import com.smartedu.modules.authentication.service.PasswordResetService;
import com.smartedu.modules.authentication.service.RegistrationService;

@RestController
@RequestMapping("/api/auth")
public class AuthenticationController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final PasswordResetService passwordResetService;

    public AuthenticationController(
            RegistrationService registrationService,
            AuthenticationService authenticationService,
            PasswordResetService passwordResetService) {
        this.registrationService = registrationService;
        this.authenticationService = authenticationService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authenticationService.login(request), "Login successful");
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        authenticationService.logout(
                authorization != null && authorization.startsWith("Bearer ")
                        ? authorization.substring(7).trim()
                        : null);
        return ApiResponse.success(null, "Logout successful");
    }

    @PostMapping("/forgot-password")
    public ApiResponse<Void> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ApiResponse.success(null, "If the email exists, a reset token has been sent");
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ApiResponse.success(null, "Password reset successfully");
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
