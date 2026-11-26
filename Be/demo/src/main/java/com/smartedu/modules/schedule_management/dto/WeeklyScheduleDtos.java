package com.smartedu.modules.schedule_management.dto;

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
            String sectionCode,
            String instructorName,
            Integer weekday,
            Integer startPeriod,
            Integer endPeriod,
            LocalTime startTime,
            LocalTime endTime,
            String roomName
    ) {
    }

    public record WeeklyScheduleResponse(
            Long optionId,
            String optionName,
            List<WeeklyMeetingDto> meetings
    ) {
    }
}
