package com.smartedu.modules.schedule_management.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.academic_management.entity.SectionMeeting;
import com.smartedu.modules.schedule_management.dto.WeeklyScheduleDtos;
import com.smartedu.modules.schedule_management.entity.ScheduleOption;
import com.smartedu.modules.schedule_management.repository.ScheduleOptionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WeeklyScheduleService {

    private final ScheduleOptionRepository optionRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional(readOnly = true)
    public WeeklyScheduleDtos.WeeklyScheduleResponse getWeeklySchedule(
            Long optionId
    ) {
        Long userId = currentUserResolver.requireCurrentUser().getId();

        ScheduleOption option = optionRepository
                .findByIdAndScheduleGenerationRegistrationPlanUserId(
                        optionId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy thời khóa biểu.",
                        HttpStatus.NOT_FOUND
                ));

        List<WeeklyScheduleDtos.WeeklyMeetingDto> meetings =
                option.getSections().stream()
                        .flatMap(item -> {
                            var section = item.getCourseSection();

                            return section.getMeetings().stream()
                                    .map(meeting -> toDto(section, meeting));
                        })
                        .sorted(
                                Comparator.comparing(
                                        WeeklyScheduleDtos.WeeklyMeetingDto::weekday
                                ).thenComparing(
                                        WeeklyScheduleDtos.WeeklyMeetingDto::startPeriod
                                )
                        )
                        .toList();

        return new WeeklyScheduleDtos.WeeklyScheduleResponse(
                option.getId(),
                option.getOptionName(),
                meetings
        );
    }

    private WeeklyScheduleDtos.WeeklyMeetingDto toDto(
            com.smartedu.modules.academic_management.entity.CourseSection section,
            SectionMeeting meeting
    ) {
        String roomName = meeting.getRoom() == null
                ? null
                : meeting.getRoom().getRoomName();

        return new WeeklyScheduleDtos.WeeklyMeetingDto(
                section.getId(),
                section.getCourse().getId(),
                section.getCourse().getCourseCode(),
                section.getCourse().getCourseName(),
                section.getSectionCode(),
                section.getInstructorName(),
                meeting.getWeekday(),
                meeting.getStartPeriod(),
                meeting.getEndPeriod(),
                meeting.getStartTime(),
                meeting.getEndTime(),
                roomName
        );
    }
}
