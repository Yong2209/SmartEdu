package com.smartedu.modules.schedule_management.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.academic_management.entity.CourseSection;
import com.smartedu.modules.academic_management.entity.SectionMeeting;
import com.smartedu.modules.academic_management.repository.CourseSectionRepository;
import com.smartedu.modules.course_registration.entity.RegistrationPlan;
import com.smartedu.modules.course_registration.entity.RegistrationPlanCourse;
import com.smartedu.modules.course_registration.entity.RegistrationPreference;
import com.smartedu.modules.course_registration.repository.RegistrationPlanRepository;
import com.smartedu.modules.course_registration.repository.RegistrationPreferenceRepository;
import com.smartedu.modules.schedule_management.dto.ScheduleDtos;
import com.smartedu.modules.schedule_management.entity.ScheduleGeneration;
import com.smartedu.modules.schedule_management.entity.ScheduleOption;
import com.smartedu.modules.schedule_management.entity.ScheduleOptionEvaluation;
import com.smartedu.modules.schedule_management.entity.ScheduleOptionSection;
import com.smartedu.modules.schedule_management.repository.ScheduleGenerationRepository;
import com.smartedu.modules.schedule_management.repository.ScheduleOptionRepository;

import lombok.RequiredArgsConstructor;



@Service
@RequiredArgsConstructor
public class ScheduleGenerationService {

    private final RegistrationPlanRepository planRepository;
    private final RegistrationPreferenceRepository preferenceRepository;
    private final CourseSectionRepository sectionRepository;
    private final ScheduleGenerationRepository generationRepository;
    private final ScheduleOptionRepository optionRepository;
    private final CurrentUserResolver currentUserResolver;

    private static final String ALGORITHM_VERSION = "1.0";

    @Transactional
    public ScheduleDtos.GenerateResponse generate(
            ScheduleDtos.GenerateRequest request
    ) {
        Long userId = currentUserResolver.requireCurrentUser().getId();

        RegistrationPlan plan = planRepository
                .findByIdAndUserId(request.registrationPlanId(), userId)
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy kế hoạch đăng ký.",
                        HttpStatus.NOT_FOUND
                ));

        List<RegistrationPlanCourse> selectedCourses = plan.getPlanCourses();

        if (selectedCourses == null || selectedCourses.isEmpty()) {
            throw new BusinessException(
                    "Kế hoạch chưa có học phần nào.",
                    HttpStatus.BAD_REQUEST
            );
        }

        RegistrationPreference preference = preferenceRepository
                .findByRegistrationPlanId(plan.getId())
                .orElse(null);

        List<List<CourseSection>> candidateGroups = new ArrayList<>();

        for (RegistrationPlanCourse selected : selectedCourses) {

            Long courseId = selected.getCourse().getId();

            List<CourseSection> sections = sectionRepository
                    .findBySemesterIdAndCourseIdOrderBySectionCodeAsc(
                            plan.getSemester().getId(),
                            courseId
                    );

            sections = sections.stream()
                    .filter(this::isAvailable)
                    .toList();

            if (sections.isEmpty()) {
                throw new BusinessException(
                        "Không có lớp học phần còn chỗ cho học phần "
                                + selected.getCourse().getCourseCode(),
                        HttpStatus.BAD_REQUEST
                );
            }

            candidateGroups.add(sections);
        }

        List<List<CourseSection>> candidates = new ArrayList<>();

        buildCombinations(
                candidateGroups,
                0,
                new ArrayList<>(),
                candidates,
                10000
        );

        long totalCandidates = candidates.size();

        List<List<CourseSection>> feasible = candidates.stream()
                .filter(this::hasNoScheduleConflict)
                .filter(list -> satisfiesCreditLimit(list, preference))
                .sorted(
                        Comparator.comparing(
                                (List<CourseSection> list) ->
                                        calculateScore(list, preference)
                        ).reversed()
                )
                .toList();

        int maxOptions = request.maxOptions() == null
                ? 5
                : Math.max(1, Math.min(request.maxOptions(), 20));

        int generationNo = generationRepository
                .findTopByRegistrationPlanIdOrderByGenerationNoDesc(plan.getId())
                .map(g -> g.getGenerationNo() + 1)
                .orElse(1);

        ScheduleGeneration generation = ScheduleGeneration.builder()
                .registrationPlan(plan)
                .generationNo(generationNo)
                .algorithmVersion(ALGORITHM_VERSION)
                .generationStatus("PROCESSING")
                .totalCandidates(totalCandidates)
                .feasibleCandidates((long) feasible.size())
                .startedAt(java.time.LocalDateTime.now())
                .build();

        generation = generationRepository.save(generation);

        List<ScheduleDtos.OptionDto> result = new ArrayList<>();

        int rank = 1;

        for (List<CourseSection> combination : feasible) {

            if (rank > maxOptions) {
                break;
            }

            BigDecimal score = calculateScore(combination, preference);
            BigDecimal credits = calculateCredits(combination);

            ScheduleOption option = ScheduleOption.builder()
                    .scheduleGeneration(generation)
                    .optionName("Phương án " + rank)
                    .optionRank(rank)
                    .totalCredits(credits)
                    .totalScore(score)
                    .isFeasible(true)
                    .optionType("GENERATED")
                    .build();

            for (CourseSection section : combination) {
                ScheduleOptionSection item = ScheduleOptionSection.builder()
                        .scheduleOption(option)
                        .courseSection(section)
                        .build();

                option.getSections().add(item);
            }

            addEvaluations(option, combination, preference);
            addWarnings(option, combination);

            optionRepository.save(option);

            result.add(toOptionDto(option, combination));

            rank++;
        }

        generation.setGenerationStatus("COMPLETED");
        generation.setCompletedAt(java.time.LocalDateTime.now());

        generationRepository.save(generation);

        return new ScheduleDtos.GenerateResponse(
                generation.getId(),
                generation.getGenerationNo(),
                generation.getGenerationStatus(),
                generation.getTotalCandidates(),
                generation.getFeasibleCandidates(),
                result
        );
    }

    private boolean isAvailable(CourseSection section) {
        return "OPEN".equalsIgnoreCase(section.getSectionStatus())
                && section.getCurrentEnrollment() != null
                && section.getMaxCapacity() != null
                && section.getCurrentEnrollment() < section.getMaxCapacity();
    }

    private void buildCombinations(
            List<List<CourseSection>> groups,
            int index,
            List<CourseSection> current,
            List<List<CourseSection>> result,
            int limit
    ) {
        if (result.size() >= limit) {
            return;
        }

        if (index == groups.size()) {
            result.add(new ArrayList<>(current));
            return;
        }

        for (CourseSection section : groups.get(index)) {
            current.add(section);

            buildCombinations(
                    groups,
                    index + 1,
                    current,
                    result,
                    limit
            );

            current.remove(current.size() - 1);

            if (result.size() >= limit) {
                break;
            }
        }
    }

    private boolean hasNoScheduleConflict(List<CourseSection> sections) {

        List<SectionMeeting> meetings = sections.stream()
                .flatMap(section -> section.getMeetings().stream())
                .toList();

        for (int i = 0; i < meetings.size(); i++) {
            for (int j = i + 1; j < meetings.size(); j++) {

                SectionMeeting a = meetings.get(i);
                SectionMeeting b = meetings.get(j);

                if (!a.getWeekday().equals(b.getWeekday())) {
                    continue;
                }

                boolean periodOverlap =
                        a.getStartPeriod() <= b.getEndPeriod()
                        && b.getStartPeriod() <= a.getEndPeriod();

                boolean timeOverlap =
                        a.getStartTime() != null
                        && a.getEndTime() != null
                        && b.getStartTime() != null
                        && b.getEndTime() != null
                        && a.getStartTime().isBefore(b.getEndTime())
                        && b.getStartTime().isBefore(a.getEndTime());

                if (periodOverlap || timeOverlap) {
                    return false;
                }
            }
        }

        return true;
    }

    private boolean satisfiesCreditLimit(
            List<CourseSection> sections,
            RegistrationPreference preference
    ) {
        if (preference == null) {
            return true;
        }

        BigDecimal credits = calculateCredits(sections);

        if (preference.getMinCredits() != null
                && credits.compareTo(preference.getMinCredits()) < 0) {
            return false;
        }

        if (preference.getMaxCredits() != null
                && credits.compareTo(preference.getMaxCredits()) > 0) {
            return false;
        }

        return true;
    }

    private BigDecimal calculateCredits(List<CourseSection> sections) {
        return sections.stream()
                .map(section -> section.getCourse().getCredits())
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal calculateScore(
            List<CourseSection> sections,
            RegistrationPreference preference
    ) {
        double score = 100.0;

        if (preference == null) {
            return BigDecimal.valueOf(score);
        }

        List<SectionMeeting> meetings = sections.stream()
                .flatMap(section -> section.getMeetings().stream())
                .toList();

        if ("MORNING".equalsIgnoreCase(preference.getPreferredShift())) {
            long morning = meetings.stream()
                    .filter(m -> m.getStartPeriod() <= 6)
                    .count();

            score += morning * 5.0;
        }

        if ("AFTERNOON".equalsIgnoreCase(preference.getPreferredShift())) {
            long afternoon = meetings.stream()
                    .filter(m -> m.getStartPeriod() >= 7)
                    .count();

            score += afternoon * 5.0;
        }

        if (Boolean.TRUE.equals(preference.getAvoidSaturday())) {
            long saturday = meetings.stream()
                    .filter(m -> m.getWeekday() == 6)
                    .count();

            score -= saturday * 10.0;
        }

        if (Boolean.TRUE.equals(preference.getCompactDays())) {
            Set<Integer> days = new HashSet<>();

            meetings.forEach(m -> days.add(m.getWeekday()));

            score += Math.max(0, 7 - days.size()) * 2.0;
        }

        if (Boolean.TRUE.equals(preference.getMinimizeGaps())) {
            score -= calculateGapPenalty(meetings);
        }

        return BigDecimal.valueOf(score).setScale(
                4,
                java.math.RoundingMode.HALF_UP
        );
    }

    
private double calculateGapPenalty(List<SectionMeeting> meetings) {

    double penalty = 0;

    for (int day = 1; day <= 7; day++) {

        // Tạo biến cố định để sử dụng bên trong lambda
        final int currentDay = day;

        List<SectionMeeting> dayMeetings = meetings.stream()
                .filter(m -> m.getWeekday() == currentDay)
                .sorted(Comparator.comparing(SectionMeeting::getStartPeriod))
                .toList();

        for (int i = 1; i < dayMeetings.size(); i++) {
            int gap = dayMeetings.get(i).getStartPeriod()
                    - dayMeetings.get(i - 1).getEndPeriod() - 1;

            if (gap > 0) {
                penalty += gap * 2.0;
            }
        }
    }

    return penalty;
}


    private void addEvaluations(
            ScheduleOption option,
            List<CourseSection> sections,
            RegistrationPreference preference
    ) {
        List<SectionMeeting> meetings = sections.stream()
                .flatMap(section -> section.getMeetings().stream())
                .toList();

        long morning = meetings.stream()
                .filter(m -> m.getStartPeriod() <= 6)
                .count();

        long afternoon = meetings.stream()
                .filter(m -> m.getStartPeriod() >= 7)
                .count();

        long saturday = meetings.stream()
                .filter(m -> m.getWeekday() == 6)
                .count();

        option.getEvaluations().add(
                evaluation(
                        option,
                        "MORNING",
                        "Ưu tiên buổi sáng",
                        morning,
                        "Số buổi học bắt đầu trước hoặc tại tiết 6."
                )
        );

        option.getEvaluations().add(
                evaluation(
                        option,
                        "AFTERNOON",
                        "Ưu tiên buổi chiều",
                        afternoon,
                        "Số buổi học bắt đầu từ tiết 7."
                )
        );

        option.getEvaluations().add(
                evaluation(
                        option,
                        "SATURDAY",
                        "Hạn chế thứ Bảy",
                        -saturday,
                        "Số buổi học vào thứ Bảy."
                )
        );

        option.getEvaluations().add(
                evaluation(
                        option,
                        "COMPACT",
                        "Lịch học tập trung",
                        7 - meetings.stream()
                                .map(SectionMeeting::getWeekday)
                                .distinct()
                                .count(),
                        "Số ngày trong tuần không có lịch học."
                )
        );
    }

    private ScheduleOptionEvaluation evaluation(
            ScheduleOption option,
            String code,
            String name,
            long score,
            String explanation
    ) {
        return ScheduleOptionEvaluation.builder()
                .scheduleOption(option)
                .criterionCode(code)
                .criterionName(name)
                .scoreValue(BigDecimal.valueOf(score))
                .weightValue(BigDecimal.ONE)
                .explanation(explanation)
                .build();
    }

    private void addWarnings(
            ScheduleOption option,
            List<CourseSection> sections
    ) {
        // Chỗ này sẽ được mở rộng để kiểm tra di chuyển giữa các cơ sở.
        // Cần biết chính xác quan hệ Room -> Campus trong project
        // trước khi so sánh khoảng cách giữa hai cơ sở.
    }

    private ScheduleDtos.OptionDto toOptionDto(
            ScheduleOption option,
            List<CourseSection> sections
    ) {
        List<ScheduleDtos.SectionDto> sectionDtos = sections.stream()
                .map(section -> new ScheduleDtos.SectionDto(
                        section.getId(),
                        section.getCourse().getId(),
                        section.getCourse().getCourseCode(),
                        section.getCourse().getCourseName(),
                        section.getCourse().getCredits(),
                        section.getSectionCode(),
                        section.getInstructorName(),
                        section.getMaxCapacity(),
                        section.getCurrentEnrollment(),
                        section.getMeetings().stream()
                                .map(this::toMeetingDto)
                                .toList()
                ))
                .toList();

        List<ScheduleDtos.EvaluationDto> evaluations =
                option.getEvaluations().stream()
                        .map(e -> new ScheduleDtos.EvaluationDto(
                                e.getCriterionCode(),
                                e.getCriterionName(),
                                e.getScoreValue(),
                                e.getWeightValue(),
                                e.getExplanation()
                        ))
                        .toList();

        List<ScheduleDtos.WarningDto> warnings =
                option.getWarnings().stream()
                        .map(w -> new ScheduleDtos.WarningDto(
                                w.getWarningType(),
                                w.getSeverity(),
                                w.getWarningMessage()
                        ))
                        .toList();

        return new ScheduleDtos.OptionDto(
                option.getId(),
                option.getOptionName(),
                option.getOptionRank(),
                option.getTotalCredits(),
                option.getTotalScore(),
                option.getIsFeasible(),
                sectionDtos,
                evaluations,
                warnings
        );
    }

    private ScheduleDtos.MeetingDto toMeetingDto(SectionMeeting meeting) {
        String roomName = meeting.getRoom() == null
                ? null
                : meeting.getRoom().getRoomName();

        return new ScheduleDtos.MeetingDto(
                meeting.getWeekday(),
                meeting.getStartPeriod(),
                meeting.getEndPeriod(),
                meeting.getStartTime(),
                meeting.getEndTime(),
                roomName,
                null
        );
    }
}
