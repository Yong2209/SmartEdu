package com.smartedu.modules.schedule_management.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.academic_management.entity.CourseSection;
import com.smartedu.modules.academic_management.entity.SectionMeeting;
import com.smartedu.modules.academic_management.repository.CourseSectionRepository;
import com.smartedu.modules.schedule_management.dto.ScheduleEditDtos;
import com.smartedu.modules.schedule_management.entity.ScheduleOption;
import com.smartedu.modules.schedule_management.entity.ScheduleOptionSection;
import com.smartedu.modules.schedule_management.repository.ScheduleOptionRepository;
import com.smartedu.modules.schedule_management.dto.ScheduleDtos;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleEditService {

    private final ScheduleOptionRepository optionRepository;
    private final CourseSectionRepository sectionRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional(readOnly = true)
    public ScheduleEditDtos.CompareResponse compare(
            ScheduleEditDtos.CompareRequest request
    ) {
        if (request.optionIds().size() < 2 || request.optionIds().size() > 3) {
            throw new BusinessException(
                    "Chỉ được so sánh từ 2 đến 3 phương án.",
                    HttpStatus.BAD_REQUEST
            );
        }

        Long userId = currentUserResolver.requireCurrentUser().getId();

        List<ScheduleDtos.OptionDto> results = new ArrayList<>();

        for (Long id : request.optionIds()) {
            if (results.stream().anyMatch(option -> option.optionId().equals(id))) {
                throw new BusinessException(
                        "Danh sách so sánh không được chứa phương án trùng lặp.",
                        HttpStatus.BAD_REQUEST
                );
            }
            ScheduleOption option = getOwnedOption(id, userId);

            List<ScheduleDtos.SectionDto> sections =
                    option.getSections().stream()
                            .map(item -> {
                                CourseSection section = item.getCourseSection();

                                List<ScheduleDtos.MeetingDto> meetings =
                                        section.getMeetings().stream()
                                                .map(m -> new ScheduleDtos.MeetingDto(
                                                        m.getWeekday(),
                                                        m.getStartPeriod(),
                                                        m.getEndPeriod(),
                                                        m.getStartTime(),
                                                        m.getEndTime(),
                                                        m.getRoom() == null
                                                                ? null
                                                                : m.getRoom().getRoomName(),
                                                        m.getRoom() == null || m.getRoom().getCampus() == null
                                                                ? null
                                                                : m.getRoom().getCampus().getCampusName()
                                                ))
                                                .toList();

                                return new ScheduleDtos.SectionDto(
                                        section.getId(),
                                        section.getCourse().getId(),
                                        section.getCourse().getCourseCode(),
                                        section.getCourse().getCourseName(),
                                        section.getCourse().getCredits(),
                                        section.getSectionCode(),
                                        section.getInstructorName(),
                                        section.getMaxCapacity(),
                                        section.getCurrentEnrollment(),
                                        meetings
                                );
                            })
                            .toList();

            results.add(new ScheduleDtos.OptionDto(
                    option.getId(),
                    option.getOptionName(),
                    option.getOptionRank(),
                    option.getTotalCredits(),
                    option.getTotalScore(),
                    option.getIsFeasible(),
                    sections,
                    List.of(),
                    List.of()
            ));
        }

        return new ScheduleEditDtos.CompareResponse(results);
    }

    @Transactional
    public ScheduleOption replaceSection(
            Long optionId,
            ScheduleEditDtos.ReplaceSectionRequest request
    ) {
        Long userId = currentUserResolver.requireCurrentUser().getId();

        ScheduleOption original = getOwnedOption(optionId, userId);

        ScheduleOptionSection oldItem = original.getSections().stream()
                .filter(item -> item.getCourseSection().getId()
                        .equals(request.oldSectionId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(
                        "Lớp học phần cần thay không thuộc phương án.",
                        HttpStatus.BAD_REQUEST
                ));

        CourseSection oldSection = oldItem.getCourseSection();

        CourseSection newSection = sectionRepository.findById(
                request.newSectionId()
        ).orElseThrow(() -> new BusinessException(
                "Không tìm thấy lớp học phần mới.",
                HttpStatus.NOT_FOUND
        ));

        if (!oldSection.getCourse().getId().equals(newSection.getCourse().getId())) {
            throw new BusinessException(
                    "Chỉ được thay bằng lớp học phần của cùng một môn.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!oldSection.getSemester().getId()
                .equals(newSection.getSemester().getId())) {
            throw new BusinessException(
                    "Hai lớp học phần không thuộc cùng học kỳ.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!"OPEN".equalsIgnoreCase(newSection.getSectionStatus())
                || newSection.getCurrentEnrollment() >= newSection.getMaxCapacity()) {
            throw new BusinessException(
                    "Lớp học phần mới không còn chỗ hoặc không mở đăng ký.",
                    HttpStatus.BAD_REQUEST
            );
        }

        List<SectionMeeting> otherMeetings = original.getSections().stream()
                .filter(item -> !item.getCourseSection().getId()
                        .equals(oldSection.getId()))
                .flatMap(item -> item.getCourseSection().getMeetings().stream())
                .toList();

        for (SectionMeeting newMeeting : newSection.getMeetings()) {
            for (SectionMeeting other : otherMeetings) {

                if (!newMeeting.getWeekday().equals(other.getWeekday())) {
                    continue;
                }

                boolean conflict =
                        newMeeting.getStartPeriod() <= other.getEndPeriod()
                        && other.getStartPeriod() <= newMeeting.getEndPeriod();
                boolean timeConflict = newMeeting.getStartTime() != null
                        && newMeeting.getEndTime() != null
                        && other.getStartTime() != null
                        && other.getEndTime() != null
                        && newMeeting.getStartTime().isBefore(other.getEndTime())
                        && other.getStartTime().isBefore(newMeeting.getEndTime());

                if (conflict || timeConflict) {
                    throw new BusinessException(
                            "Lớp học phần mới bị trùng lịch với một môn khác.",
                            HttpStatus.CONFLICT
                    );
                }
            }
        }

        ScheduleOption edited = ScheduleOption.builder()
                .scheduleGeneration(original.getScheduleGeneration())
                .parentOption(original)
                .optionName(
                        request.newOptionName() == null
                                || request.newOptionName().isBlank()
                                ? original.getOptionName() + " (Đã chỉnh sửa)"
                                : request.newOptionName()
                )
                .optionRank(null)
                .totalCredits(original.getTotalCredits())
                .totalScore(original.getTotalScore())
                .isFeasible(true)
                .optionType("MANUAL")
                .build();

        for (ScheduleOptionSection item : original.getSections()) {
            CourseSection selected = item.getCourseSection();

            if (selected.getId().equals(oldSection.getId())) {
                selected = newSection;
            }

            edited.getSections().add(
                    ScheduleOptionSection.builder()
                            .scheduleOption(edited)
                            .courseSection(selected)
                            .build()
            );
        }

        recalculateEvaluation(edited, original);

        return optionRepository.save(edited);
    }

    private ScheduleOption getOwnedOption(Long optionId, Long userId) {
        return optionRepository
                .findByIdAndScheduleGenerationRegistrationPlanUserId(
                        optionId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy phương án.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private void recalculateEvaluation(ScheduleOption edited, ScheduleOption original) {
        List<SectionMeeting> meetings = edited.getSections().stream()
                .flatMap(item -> item.getCourseSection().getMeetings().stream())
                .toList();
        long morning = meetings.stream().filter(m -> m.getStartPeriod() <= 6).count();
        long afternoon = meetings.stream().filter(m -> m.getStartPeriod() >= 7).count();
        long saturday = meetings.stream().filter(m -> m.getWeekday() == 6).count();
        long freeDays = 7 - meetings.stream().map(SectionMeeting::getWeekday).distinct().count();
        List<Integer> gaps = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            final int currentDay = day;
            List<SectionMeeting> dayMeetings = meetings.stream()
                    .filter(m -> m.getWeekday() == currentDay)
                    .sorted(java.util.Comparator.comparing(SectionMeeting::getStartPeriod))
                    .toList();
            for (int i = 1; i < dayMeetings.size(); i++) {
                int gap = dayMeetings.get(i).getStartPeriod() - dayMeetings.get(i - 1).getEndPeriod() - 1;
                if (gap > 0) gaps.add(gap);
            }
        }
        Integer maxGap = original.getScheduleGeneration().getRegistrationPlan().getPreference() == null
                ? null
                : original.getScheduleGeneration().getRegistrationPlan().getPreference().getMaxGapPeriods();

        java.math.BigDecimal total = new java.math.BigDecimal("100.0");
        for (var previous : original.getEvaluations()) {
            long rawScore = switch (previous.getCriterionCode()) {
                case "MORNING" -> morning;
                case "AFTERNOON" -> afternoon;
                case "SATURDAY" -> -saturday;
                case "COMPACT" -> Math.min(2, freeDays);
                case "GAPS" -> -gaps.stream().mapToInt(Integer::intValue).sum();
                case "MAX_GAP" -> maxGap == null ? 0 : -gaps.stream().mapToInt(gap -> Math.max(0, gap - maxGap)).sum();
                default -> previous.getScoreValue().longValue();
            };
            edited.getEvaluations().add(com.smartedu.modules.schedule_management.entity.ScheduleOptionEvaluation.builder()
                    .scheduleOption(edited)
                    .criterionCode(previous.getCriterionCode())
                    .criterionName(previous.getCriterionName())
                    .scoreValue(java.math.BigDecimal.valueOf(rawScore))
                    .weightValue(previous.getWeightValue())
                    .explanation(previous.getExplanation())
                    .build());
            total = total.add(java.math.BigDecimal.valueOf(rawScore).multiply(previous.getWeightValue()));
        }
        edited.setTotalScore(total.setScale(4, java.math.RoundingMode.HALF_UP));
    }
}
