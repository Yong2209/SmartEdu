package com.smartedu.modules.course_registration.controller;

import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.smartedu.modules.course_registration.dto.RegistrationDtos.AcademicRecordResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.TranscriptImportResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.UpsertAcademicRecordRequest;
import com.smartedu.modules.course_registration.service.StudentAcademicRecordService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/registration/academic-records")
@RequiredArgsConstructor
public class StudentAcademicRecordController {

    private final StudentAcademicRecordService academicRecordService;
    private final CurrentUserResolver currentUserResolver;

    @GetMapping
    public ApiResponse<List<AcademicRecordResponse>> listMyRecords() {
        return ApiResponse.success(academicRecordService.listMyRecords(currentUserResolver.requireCurrentUser()));
    }

    @GetMapping("/passed")
    public ApiResponse<List<AcademicRecordResponse>> listPassedRecords() {
        return ApiResponse.success(academicRecordService.listPassedRecords(currentUserResolver.requireCurrentUser()));
    }

    @PostMapping
    public ApiResponse<AcademicRecordResponse> upsertRecord(
            @Valid @RequestBody UpsertAcademicRecordRequest request) {
        return ApiResponse.success(
                academicRecordService.upsertRecord(currentUserResolver.requireCurrentUser(), request),
                "Đã lưu kết quả học phần đã hoàn thành."
        );
    }

    @DeleteMapping("/{recordId}")
    public ApiResponse<Void> deleteRecord(@PathVariable Long recordId) {
        academicRecordService.deleteRecord(currentUserResolver.requireCurrentUser(), recordId);
        return ApiResponse.success(null, "Đã xóa bản ghi học phần.");
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TranscriptImportResponse> importTranscript(
            @RequestParam(value = "semesterId", required = false) Long semesterId,
            @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(
                academicRecordService.importTranscript(currentUserResolver.requireCurrentUser(), semesterId, file),
                "Đã xử lý bảng điểm tải lên."
        );
    }
}
