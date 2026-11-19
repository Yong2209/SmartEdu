package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.AuthDtos.UserProfileResponse;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AuthContextHelper authHelper;

    // US35 & US36: Import danh mục học phần & lớp học phần mở từ Excel
    @PostMapping(value = "/catalog/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> importCourseCatalog(
            @RequestParam Long semesterId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("Import danh mục học phần hoàn tất.",
                adminService.importCourseCatalog(authHelper.getCurrentUser(), semesterId, file));
    }

    // US37: Quản lý danh sách người dùng
    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> listUsers(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.listUsers(role, status, keyword, page, size));
    }

    // US38: Khóa / Mở khóa tài khoản
    @PatchMapping("/users/{userId}/status")
    public ApiResponse<UserProfileResponse> updateUserStatus(
            @PathVariable Long userId,
            @RequestParam String status,
            @RequestParam(required = false) String reason) {
        return ApiResponse.ok("Cập nhật trạng thái tài khoản thành công.",
                adminService.updateUserStatus(authHelper.getCurrentUser(), userId, status, reason));
    }

    // US38: Phân quyền vai trò người dùng
    @PatchMapping("/users/{userId}/role")
    public ApiResponse<UserProfileResponse> updateUserRole(
            @PathVariable Long userId,
            @RequestParam String roleCode) {
        return ApiResponse.ok("Cập nhật vai trò người dùng thành công.",
                adminService.updateUserRole(authHelper.getCurrentUser(), userId, roleCode));
    }

    // US39: Theo dõi nhật ký vận hành hệ thống
    @GetMapping("/monitoring/logs")
    public ApiResponse<Map<String, Object>> getSystemLogs() {
        return ApiResponse.ok(adminService.getSystemMonitoringLogs());
    }
}
