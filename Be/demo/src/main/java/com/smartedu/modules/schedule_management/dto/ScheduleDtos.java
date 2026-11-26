package com.smartedu.modules.schedule_management.dto;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class ScheduleDtos {

    private ScheduleDtos() {
    }

    // Yêu cầu tạo thời khóa biểu
    public record GenerateRequest(
            @NotNull
            @Positive
            Long registrationPlanId,

            Integer maxOptions
    ) {
    }

    // Thông tin một buổi học
    public record MeetingDto(
            Integer weekday,
            Integer startPeriod,
            Integer endPeriod,
            LocalTime startTime,
            LocalTime endTime,
            String roomName,
            String campusName
    ) {
    }

    // Thông tin lớp học phần
    public record SectionDto(
            Long sectionId,
            Long courseId,
            String courseCode,
            String courseName,
            BigDecimal credits,
            String sectionCode,
            String instructorName,
            Integer maxCapacity,
            Integer currentEnrollment,
            List<MeetingDto> meetings
    ) {
    }

    // Điểm đánh giá từng tiêu chí
    public record EvaluationDto(
            String criterionCode,
            String criterionName,
            BigDecimal scoreValue,
            BigDecimal weightValue,
            String explanation
    ) {
    }

    // Cảnh báo của phương án
    public record WarningDto(
            String warningType,
            String severity,
            String warningMessage
    ) {
    }

    // Một phương án thời khóa biểu
    public record OptionDto(
            Long optionId,
            String optionName,
            Integer optionRank,
            BigDecimal totalCredits,
            BigDecimal totalScore,
            Boolean feasible,
            List<SectionDto> sections,
            List<EvaluationDto> evaluations,
            List<WarningDto> warnings
    ) {
    }

    // Kết quả tạo thời khóa biểu
    public record GenerateResponse(
            Long generationId,
            Integer generationNo,
            String status,
            Long totalCandidates,
            Long feasibleCandidates,
            List<OptionDto> options
    ) {
    }
}
