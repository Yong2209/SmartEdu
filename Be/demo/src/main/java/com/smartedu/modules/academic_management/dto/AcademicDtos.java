package com.smartedu.modules.academic_management.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AcademicDtos {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SemesterDto {
        private Long id;
        private String semesterCode;
        private String semesterName;
        private String academicYear;
        private Integer termNo;
        private LocalDate startDate;
        private LocalDate endDate;
        private LocalDateTime registrationStartAt;
        private LocalDateTime registrationEndAt;
        private String semesterStatus;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrerequisiteRefDto {
        private Long courseId;
        private String courseCode;
        private String courseName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseDto {
        private Long id;
        private String courseCode;
        private String courseName;
        private BigDecimal credits;
        private String description;
        private Boolean isActive;
        private Integer openSectionsCount;
        private List<PrerequisiteRefDto> prerequisites;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionMeetingDto {
        private Long id;
        private Integer weekday;
        private Integer startPeriod;
        private Integer endPeriod;
        private LocalTime startTime;
        private LocalTime endTime;
        private String roomCode;
        private String roomName;
        private String campusName;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseSectionDto {
        private Long id;
        private Long semesterId;
        private Long courseId;
        private String courseCode;
        private String courseName;
        private BigDecimal credits;
        private String sectionCode;
        private String instructorName;
        private Integer maxCapacity;
        private Integer currentEnrollment;
        private String sectionStatus;
        private List<SectionMeetingDto> meetings;
    }
}
