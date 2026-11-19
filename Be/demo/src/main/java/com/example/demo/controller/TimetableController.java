package com.example.demo.controller;

import com.example.demo.common.ApiResponse;
import com.example.demo.dto.TimetableDtos.*;
import com.example.demo.entity.Course;
import com.example.demo.entity.RegistrationPreference;
import com.example.demo.entity.Semester;
import com.example.demo.security.AuthContextHelper;
import com.example.demo.service.TimetableService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/timetable")
@RequiredArgsConstructor
public class TimetableController {

    private final TimetableService timetableService;
    private final AuthContextHelper authHelper;

    @GetMapping("/semesters")
    public ApiResponse<List<Semester>> getSemesters() {
        return ApiResponse.ok(timetableService.getAllSemesters());
    }

    @GetMapping("/courses")
    public ApiResponse<List<Course>> getCourses() {
        return ApiResponse.ok(timetableService.getActiveCourses());
    }

    @GetMapping("/sections")
    public ApiResponse<List<CourseSectionDto>> getSections(
            @RequestParam Long semesterId,
            @RequestParam(required = false) List<Long> courseIds) {
        return ApiResponse.ok(timetableService.getOpenSectionsBySemester(semesterId, courseIds));
    }

    // US20 & US21: Import danh sách học phần từ Excel/CSV
    @PostMapping(value = "/import-courses", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<Map<String, Object>> importCourses(
            @RequestParam Long semesterId,
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok("Xử lý tệp danh sách học phần hoàn tất.",
                timetableService.importRegistrationCourses(authHelper.getCurrentUser(), semesterId, file));
    }

    // US22: Tạo kế hoạch chọn học phần thủ công
    @PostMapping("/plans")
    public ApiResponse<Map<String, Object>> createPlan(@Valid @RequestBody CreatePlanRequest req) {
        return ApiResponse.ok("Đã tạo kế hoạch đăng ký học phần.",
                timetableService.createRegistrationPlan(authHelper.getCurrentUser(), req));
    }

    // US23: Cấu hình tiêu chí ưu tiên xếp lịch
    @PutMapping("/plans/{planId}/preferences")
    public ApiResponse<RegistrationPreference> savePreferences(
            @PathVariable Long planId,
            @RequestBody PreferenceRequest req) {
        return ApiResponse.ok("Đã lưu tiêu chí ưu tiên xếp lịch.",
                timetableService.savePreferences(authHelper.getCurrentUser(), planId, req));
    }

    // US24 - US27: Sinh phương án TKB tối ưu bằng Backtracking
    @PostMapping("/plans/{planId}/generate")
    public ApiResponse<GenerationResultResponse> generateSchedules(@PathVariable Long planId) {
        return ApiResponse.ok(timetableService.generateOptimalSchedules(authHelper.getCurrentUser(), planId));
    }

    // US28 & US29: Chỉnh sửa thủ công & kiểm tra xung đột thời gian thực
    @PostMapping("/options/{optionId}/manual-edit")
    public ApiResponse<ScheduleOptionDto> manualEditOption(
            @PathVariable Long optionId,
            @Valid @RequestBody ManualEditOptionRequest req) {
        return ApiResponse.ok("Đã tạo phiên bản thời khóa biểu chỉnh sửa thủ công.",
                timetableService.createManualEditedOption(authHelper.getCurrentUser(), optionId, req));
    }

    // US31 & US32: Lưu, xem danh sách & xóa TKB yêu thích
    @PostMapping("/saved")
    public ApiResponse<SavedScheduleDto> saveSchedule(@Valid @RequestBody SaveScheduleRequest req) {
        return ApiResponse.ok("Đã lưu phương án thời khóa biểu yêu thích.",
                timetableService.saveFavoriteSchedule(authHelper.getCurrentUser(), req));
    }

    @GetMapping("/saved")
    public ApiResponse<List<SavedScheduleDto>> listSavedSchedules() {
        return ApiResponse.ok(timetableService.listMySavedSchedules(authHelper.getCurrentUser()));
    }

    @DeleteMapping("/saved/{savedId}")
    public ApiResponse<Void> deleteSavedSchedule(@PathVariable Long savedId) {
        timetableService.deleteSavedSchedule(authHelper.getCurrentUser(), savedId);
        return ApiResponse.ok("Đã xóa thời khóa biểu khỏi danh sách lưu.", null);
    }

    // US33 & US34: Ghi nhận xuất PNG/PDF
    @PostMapping("/options/{optionId}/export")
    public ApiResponse<Map<String, Object>> exportSchedule(
            @PathVariable Long optionId,
            @RequestParam(defaultValue = "PDF") String format) {
        return ApiResponse.ok("Xuất dữ liệu thời khóa biểu thành công.",
                timetableService.recordExportSchedule(authHelper.getCurrentUser(), optionId, format));
    }
}
