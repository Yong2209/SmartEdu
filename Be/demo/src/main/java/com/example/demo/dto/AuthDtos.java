package com.example.demo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Data;

public class AuthDtos {

    @Data
    public static class RegisterRequest {
        private String studentCode;

        @NotBlank(message = "Email không được để trống")
        @Email(message = "Định dạng email không hợp lệ")
        private String email;

        @NotBlank(message = "Mật khẩu không được để trống")
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+=-]).{8,}$",
            message = "Mật khẩu phải >= 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt"
        )
        private String password;

        @NotBlank(message = "Họ và tên không được để trống")
        private String fullName;
    }

    @Data
    public static class VerifyOtpRequest {
        @NotBlank @Email
        private String email;
        @NotBlank
        private String otp;
    }

    @Data
    public static class LoginRequest {
        @NotBlank(message = "Vui lòng nhập email, mã sinh viên hoặc username")
        private String identifier;
        @NotBlank(message = "Vui lòng nhập mật khẩu")
        private String password;
    }

    @Data
    public static class ForgotPasswordRequest {
        @NotBlank @Email
        private String email;
    }

    @Data
    public static class ResetPasswordRequest {
        @NotBlank
        private String token;
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+=-]).{8,}$",
            message = "Mật khẩu mới phải >= 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt"
        )
        private String newPassword;
    }

    @Data
    public static class UpdateProfileRequest {
        private String fullName;
        private String phone;
        private String address;
        private String gender;
        private String avatarUrl;
    }

    @Data
    public static class ChangePasswordRequest {
        @NotBlank
        private String oldPassword;
        @NotBlank
        @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_+=-]).{8,}$",
            message = "Mật khẩu mới phải >= 8 ký tự, gồm chữ hoa, chữ thường, số và ký tự đặc biệt"
        )
        private String newPassword;
    }

    @Data @Builder
    public static class UserProfileResponse {
        private Long id;
        private String studentCode;
        private String username;
        private String fullName;
        private String email;
        private String phone;
        private String address;
        private String gender;
        private String avatarUrl;
        private String role;
        private String accountStatus;
    }

    @Data @Builder
    public static class AuthResponse {
        private String accessToken;
        private String refreshToken;
        private Long expiresInMs;
        private UserProfileResponse user;
    }
}
