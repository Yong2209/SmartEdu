package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.AuthDtos.*;
import com.example.demo.entity.User;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AuthContextHelper authHelper;

    // US02: Đăng ký tài khoản sinh viên
    @PostMapping("/register")
    public ApiResponse<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok("Đăng ký thành công. Vui lòng kiểm tra mã OTP 6 chữ số.", authService.register(req));
    }

    // US03: Xác thực tài khoản qua OTP
    @PostMapping("/verify-otp")
    public ApiResponse<UserProfileResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest req) {
        return ApiResponse.ok("Xác thực email thành công! Bạn có thể đăng nhập ngay.", authService.verifyOtp(req));
    }

    @PostMapping("/resend-otp")
    public ApiResponse<Map<String, Object>> resendOtp(@RequestParam String email) {
        return ApiResponse.ok("Đã gửi lại mã OTP mới.", authService.resendOtp(email));
    }

    // US04: Đăng nhập hệ thống
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req, HttpServletRequest httpReq) {
        String ip = httpReq.getRemoteAddr();
        String ua = httpReq.getHeader("User-Agent");
        return ApiResponse.ok("Đăng nhập thành công", authService.login(req, ip, ua));
    }

    // US05: Đăng xuất & thu hồi JWT
    @PostMapping("/logout")
    public ApiResponse<Void> logout() {
        authService.logout(authHelper.getCurrentToken());
        return ApiResponse.ok("Đăng xuất thành công. Phiên làm việc đã được hủy.", null);
    }

    // US06: Quên mật khẩu & Đặt lại mật khẩu
    @PostMapping("/forgot-password")
    public ApiResponse<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        return ApiResponse.ok("Yêu cầu khôi phục mật khẩu đã được xử lý.", authService.forgotPassword(req));
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ApiResponse.ok("Đặt lại mật khẩu thành công.", null);
    }

    // US07: Xem & cập nhật hồ sơ cá nhân, đổi mật khẩu
    @GetMapping("/me")
    public ApiResponse<UserProfileResponse> getProfile() {
        User user = authHelper.getCurrentUser();
        return ApiResponse.ok(authService.toProfile(user));
    }

    @PutMapping("/me")
    public ApiResponse<UserProfileResponse> updateProfile(@Valid @RequestBody UpdateProfileRequest req) {
        User user = authHelper.getCurrentUser();
        return ApiResponse.ok("Cập nhật hồ sơ thành công.", authService.updateProfile(user, req));
    }

    @PostMapping("/change-password")
    public ApiResponse<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        User user = authHelper.getCurrentUser();
        authService.changePassword(user, req);
        return ApiResponse.ok("Đổi mật khẩu thành công.", null);
    }
}
