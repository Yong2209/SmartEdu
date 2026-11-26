package com.smartedu.modules.schedule_management.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public final class WeeklyScheduleDtos {

    private WeeklyScheduleDtos() {
    }

    public record WeeklyMeetingDto(
            Long sectionId,
            Long courseId,
            String courseCode,
            String courseName,
            BigDecimal credits,
            String sectionCode,
            String instructorName,
            Integer weekday,
            Integer startPeriod,
            Integer endPeriod,
            LocalTime startTime,
            LocalTime endTime,
            String roomName,
            String campusName
    ) {
    }

    public record WeeklyScheduleResponse(
            Long optionId,
            String optionName,
            BigDecimal totalCredits,
            BigDecimal totalScore,
            Integer optionRank,
            Long semesterId,
            LocalDate semesterStartDate,
            LocalDate semesterEndDate,
            List<WeeklyMeetingDto> meetings
    ) {
    }
}
