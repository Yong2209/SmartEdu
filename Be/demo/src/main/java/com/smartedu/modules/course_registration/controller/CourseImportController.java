package com.smartedu.modules.course_registration.controller;

import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ConfirmImportRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ImportTemplateGuideResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ImportValidationResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.RegistrationPlanResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ValidateCourseListRequest;
import com.smartedu.modules.course_registration.service.CourseImportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/registration/imports")
@RequiredArgsConstructor
public class CourseImportController {

    private final CourseImportService courseImportService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping("/template")
    public ApiResponse<ImportTemplateGuideResponse> getTemplateGuide() {
        return ApiResponse.success(courseImportService.getTemplateGuide());
    }

    @GetMapping("/template/download")
    public ResponseEntity<byte[]> downloadTemplate(@RequestParam(defaultValue = "csv") String format) {
        byte[] bytes = courseImportService.downloadSampleTemplate(format);
        boolean isXlsx = "xlsx".equalsIgnoreCase(format);
        String filename = isXlsx ? "danh_muc_hoc_phan_mau.xlsx" : "danh_muc_hoc_phan_mau.csv";
        MediaType mediaType = isXlsx
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv; charset=UTF-8");

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(mediaType)
                .body(bytes);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<ImportValidationResponse> uploadCourseFile(
            @RequestParam("semesterId") Long semesterId,
            @RequestParam("file") MultipartFile file) {
        ImportValidationResponse response = courseImportService.uploadCourseFile(
                currentUserResolver.requireCurrentUser(), semesterId, file);
        String msg = response.isValid()
                ? "Tải lên và kiểm tra danh sách học phần hợp lệ."
                : "Tệp có " + response.getErrorRows() + " lỗi dữ liệu cần kiểm tra.";
        return ApiResponse.success(response, msg);
    }

    @PostMapping("/validate")
    public ApiResponse<ImportValidationResponse> validateCourseList(
            @Valid @RequestBody ValidateCourseListRequest request) {
        ImportValidationResponse response = courseImportService.validateCourseList(
                currentUserResolver.requireCurrentUser(), request);
        String msg = response.isValid()
                ? "Danh sách học phần hợp lệ."
                : "Danh sách còn " + response.getErrorRows() + " lỗi dữ liệu.";
        return ApiResponse.success(response, msg);
    }

    @PostMapping("/{importId}/confirm")
    public ApiResponse<RegistrationPlanResponse> confirmImport(
            @PathVariable Long importId,
            @RequestBody(required = false) ConfirmImportRequest request) {
        return ApiResponse.success(
                courseImportService.confirmImport(currentUserResolver.requireCurrentUser(), importId, request),
                "Đã xác nhận và lưu danh sách học phần vào kế hoạch đăng ký."
        );
    }

    @GetMapping
    public ApiResponse<List<ImportValidationResponse>> listMyImports() {
        return ApiResponse.success(courseImportService.listMyImports(currentUserResolver.requireCurrentUser()));
    }

    @GetMapping("/{importId}")
    public ApiResponse<ImportValidationResponse> getImportDetail(@PathVariable Long importId) {
        return ApiResponse.success(courseImportService.getImportDetail(
                currentUserResolver.requireCurrentUser(), importId));
    }
}
