package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class TimetableDtos {

    @Data
    public static class CreatePlanRequest {
        @NotNull(message = "Vui lòng chọn học kỳ")
        private Long semesterId;
        private String planName;
        @NotEmpty(message = "Vui lòng chọn ít nhất 1 học phần")
        private List<Long> courseIds;
    }

    @Data
    public static class PreferenceRequest {
        private BigDecimal minCredits = new BigDecimal("12.0");
        private BigDecimal maxCredits = new BigDecimal("24.0");
        private String preferredShift = "ANY"; // MORNING, AFTERNOON, ANY
        private Boolean avoidSaturday = false;
        private Boolean compactDays = false;
        private Boolean minimizeGaps = false;
        private List<Integer> preferredFreeDays; // 1=Mon .. 7=Sun
        private Short maxGapPeriods;
    }

    @Data
    public static class ManualEditOptionRequest {
        private String optionName;
        @NotEmpty(message = "Danh sách lớp học phần không được trống")
        private List<Long> courseSectionIds;
    }

    @Data
    public static class SaveScheduleRequest {
        @NotNull
        private Long scheduleOptionId;
        @NotBlank(message = "Vui lòng nhập tên hiển thị cho thời khóa biểu")
        private String displayName;
    }

    @Data @Builder
    public static class SectionMeetingDto {
        private Short dayOfWeek;
        private Short startPeriod;
        private Short endPeriod;
        private String sessionType;
        private String campusCode;
        private String roomCode;
    }

    @Data @Builder
    public static class CourseSectionDto {
        private Long id;
        private Long courseId;
        private String courseCode;
        private String courseName;
        private BigDecimal credits;
        private String sectionCode;
        private String lecturerName;
        private String classGroup;
        private Integer maxCapacity;
        private Integer enrolledCount;
        private String sectionStatus;
        private List<SectionMeetingDto> meetings;
    }

    @Data @Builder
    public static class EvaluationCriterionDto {
        private String criterionCode;
        private String criterionName;
        private BigDecimal scoreValue;
        private BigDecimal weightValue;
        private String explanation;
    }

    @Data @Builder
    public static class WarningDto {
        private String warningType;
        private String severity;
        private String warningMessage;
    }

    @Data @Builder
    public static class ScheduleOptionDto {
        private Long id;
        private String optionName;
        private Integer optionRank;
        private BigDecimal totalCredits;
        private BigDecimal totalScore;
        private Boolean isFeasible;
        private String optionType;
        private List<CourseSectionDto> sections;
        private List<EvaluationCriterionDto> evaluations;
        private List<WarningDto> warnings;
    }

    @Data @Builder
    public static class GenerationResultResponse {
        private Long generationId;
        private Long planId;
        private String planName;
        private String semesterCode;
        private Long totalCandidates;
        private Long feasibleCandidates;
        private String status;
        private String message;
        private List<ScheduleOptionDto> options;
    }

    @Data @Builder
    public static class SavedScheduleDto {
        private Long id;
        private String displayName;
        private LocalDateTime savedAt;
        private ScheduleOptionDto option;
    }
}
