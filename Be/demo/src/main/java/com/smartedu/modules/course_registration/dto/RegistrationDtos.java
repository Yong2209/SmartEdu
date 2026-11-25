package com.smartedu.modules.course_registration.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class RegistrationDtos {

    // ---------------- PB20 & PB21: Course Import & Validation ----------------

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseImportRow {
        private Long rowNumber;
        private Long courseId;
        private String courseCode;
        private String courseName;
        private BigDecimal credits;
        private String sectionCode;
        private Boolean valid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportRowErrorDto {
        private Long rowNumber;
        private String fieldName;
        private String errorCode;
        private String errorMessage;
        private String rowData;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportValidationResponse {
        private Long importId;
        private Long semesterId;
        private String fileName;
        private String fileFormat;
        private String importStatus;
        private boolean valid;
        private long totalRows;
        private long validRows;
        private long errorRows;
        private LocalDateTime processedAt;
        private List<CourseImportRow> validItems;
        private List<ImportRowErrorDto> errors;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidateCourseListRequest {
        @NotNull(message = "Vui lòng chọn học kỳ")
        private Long semesterId;
        private Long importId;
        @NotEmpty(message = "Danh sách học phần không được rỗng")
        @Valid
        private List<CourseImportRowInput> items;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseImportRowInput {
        private String courseCode;
        private String courseName;
        private BigDecimal credits;
        private String sectionCode;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfirmImportRequest {
        private String planName;
        @Valid
        private List<CourseImportRowInput> items;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportTemplateGuideResponse {
        private List<String> supportedFormats;
        private long maxFileSizeBytes;
        private List<TemplateColumn> columns;
        private String sampleCsv;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateColumn {
        private String fieldName;
        private String headerName;
        private boolean required;
        private String description;
        private String example;
    }

    // ---------------- PB22: Registration Plan & Course Selection ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreatePlanRequest {
        @NotNull(message = "Vui lòng chọn học kỳ")
        private Long semesterId;
        private String planName;
        private List<Long> courseIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePlanCoursesRequest {
        @NotNull(message = "Danh sách học phần không được null")
        private List<Long> courseIds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddPlanCourseRequest {
        @NotNull(message = "Vui lòng chọn học phần")
        private Long courseId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanCourseDto {
        private Long courseId;
        private String courseCode;
        private String courseName;
        private BigDecimal credits;
        private String selectionSource;
        private Integer openSectionsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RegistrationPlanResponse {
        private Long planId;
        private Long semesterId;
        private String semesterCode;
        private String semesterName;
        private Long sourceImportId;
        private String planName;
        private String planStatus;
        private Integer courseCount;
        private BigDecimal totalCredits;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<PlanCourseDto> courses;
        private CreditLimitResponse creditLimits;
        private SchedulePreferenceResponse schedulePreferences;
    }

    // ---------------- PB23: Completed Courses / Academic Records ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsertAcademicRecordRequest {
        private Long courseId;
        private String courseCode;
        private Long semesterId;
        private BigDecimal grade;
        @NotBlank(message = "Vui lòng cung cấp trạng thái kết quả (PASSED hoặc FAILED)")
        private String resultStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AcademicRecordResponse {
        private Long recordId;
        private Long courseId;
        private String courseCode;
        private String courseName;
        private BigDecimal credits;
        private Long semesterId;
        private String semesterCode;
        private BigDecimal grade;
        private String resultStatus;
        private LocalDateTime completedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TranscriptImportResponse {
        private Long importId;
        private String fileName;
        private String importStatus;
        private long totalRows;
        private long validRows;
        private long errorRows;
        private List<AcademicRecordResponse> savedRecords;
        private List<ImportRowErrorDto> errors;
    }

    // ---------------- PB24: Credit Limits ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditLimitRequest {
        @NotNull(message = "Vui lòng nhập số tín chỉ tối thiểu")
        @DecimalMin(value = "0.0", message = "Số tín chỉ tối thiểu phải lớn hơn hoặc bằng 0")
        private BigDecimal minCredits;

        @NotNull(message = "Vui lòng nhập số tín chỉ tối đa")
        @DecimalMin(value = "0.5", message = "Số tín chỉ tối đa phải lớn hơn 0")
        private BigDecimal maxCredits;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditLimitResponse {
        private Long planId;
        private Long semesterId;
        private BigDecimal minCredits;
        private BigDecimal maxCredits;
        private boolean configured;
    }

    // ---------------- PB25: Schedule Preferences (Soft Constraints) ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchedulePreferenceRequest {
        private String preferredShift;
        private Boolean avoidSaturday = false;
        private Boolean compactDays = false;
        private Boolean minimizeGaps = false;
        private List<Integer> preferredFreeDays;
        @Min(value = 0, message = "Số tiết trống tối đa không được âm")
        @Max(value = 12, message = "Số tiết trống tối đa không hợp lệ")
        private Integer maxGapPeriods;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchedulePreferenceResponse {
        private Long planId;
        private Long semesterId;
        private String preferredShift;
        private Boolean avoidSaturday;
        private Boolean compactDays;
        private Boolean minimizeGaps;
        private List<Integer> preferredFreeDays;
        private Integer maxGapPeriods;
        private String constraintType;
    }

    // ---------------- PB26: Schedule Conflict Check ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleConflictCheckRequest {
        private Long planId;
        private List<Long> sectionIds;
        private List<List<Long>> combinations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleConflictDetail {
        private Long sectionAId;
        private String sectionACode;
        private String courseACode;
        private Long sectionBId;
        private String sectionBCode;
        private String courseBCode;
        private Integer weekday;
        private String sectionAPeriods;
        private String sectionBPeriods;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckedSectionCombination {
        private int combinationIndex;
        private List<Long> sectionIds;
        private List<String> sectionCodes;
        private boolean valid;
        private List<ScheduleConflictDetail> conflicts;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScheduleConflictCheckResponse {
        private int totalChecked;
        private int validCount;
        private int rejectedCount;
        private List<CheckedSectionCombination> validCombinations;
        private List<CheckedSectionCombination> rejectedCombinations;
    }

    // ---------------- PB27: Prerequisite Check ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrerequisiteCheckRequest {
        private Long planId;
        private List<Long> courseIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoursePrerequisiteStatus {
        private Long courseId;
        private String courseCode;
        private String courseName;
        private BigDecimal credits;
        private boolean eligible;
        private List<MissingPrerequisiteDto> missingPrerequisites;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MissingPrerequisiteDto {
        private Long courseId;
        private String courseCode;
        private String courseName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrerequisiteCheckResponse {
        private Long planId;
        private int totalChecked;
        private int eligibleCount;
        private int ineligibleCount;
        private List<CoursePrerequisiteStatus> eligibleCourses;
        private List<CoursePrerequisiteStatus> ineligibleCourses;
    }

    // ---------------- PB28: Credit Limit Check ----------------

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditLimitCheckRequest {
        private Long planId;
        private Long semesterId;
        private List<Long> sectionIds;
        private List<List<Long>> combinations;
        private List<List<Long>> courseCombinations;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckedCreditCombination {
        private int combinationIndex;
        private List<Long> sectionIds;
        private List<Long> courseIds;
        private BigDecimal totalCredits;
        private boolean passed;
        private String violationType;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreditLimitCheckResponse {
        private Long planId;
        private BigDecimal minCredits;
        private BigDecimal maxCredits;
        private int totalChecked;
        private int validCount;
        private int rejectedCount;
        private List<CheckedCreditCombination> validCombinations;
        private List<CheckedCreditCombination> rejectedCombinations;
    }
}
