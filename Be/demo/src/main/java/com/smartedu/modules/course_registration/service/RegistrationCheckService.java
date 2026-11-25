package com.smartedu.modules.course_registration.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.academic_management.entity.Course;
import com.smartedu.modules.academic_management.entity.CoursePrerequisite;
import com.smartedu.modules.academic_management.entity.CourseSection;
import com.smartedu.modules.academic_management.entity.SectionMeeting;
import com.smartedu.modules.academic_management.repository.CoursePrerequisiteRepository;
import com.smartedu.modules.academic_management.repository.CourseRepository;
import com.smartedu.modules.academic_management.repository.CourseSectionRepository;
import com.smartedu.modules.academic_management.repository.SectionMeetingRepository;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CheckedCreditCombination;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CheckedSectionCombination;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CoursePrerequisiteStatus;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitCheckRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitCheckResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.MissingPrerequisiteDto;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.PrerequisiteCheckRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.PrerequisiteCheckResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ScheduleConflictCheckRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ScheduleConflictCheckResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.ScheduleConflictDetail;
import com.smartedu.modules.course_registration.entity.RegistrationPlan;
import com.smartedu.modules.course_registration.entity.RegistrationPlanCourse;
import com.smartedu.modules.course_registration.entity.RegistrationPreference;
import com.smartedu.modules.course_registration.entity.StudentCourseRecord;
import com.smartedu.modules.course_registration.repository.RegistrationPlanRepository;
import com.smartedu.modules.course_registration.repository.RegistrationPreferenceRepository;
import com.smartedu.modules.course_registration.repository.StudentCourseRecordRepository;
import com.smartedu.modules.user_management.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationCheckService {

    private final RegistrationPlanService planService;
    private final RegistrationPlanRepository planRepository;
    private final RegistrationPreferenceRepository preferenceRepository;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final SectionMeetingRepository sectionMeetingRepository;
    private final CoursePrerequisiteRepository prerequisiteRepository;
    private final StudentCourseRecordRepository studentCourseRecordRepository;

    // ---------------- PB26: Schedule conflict checking ----------------

    @Transactional(readOnly = true)
    public ScheduleConflictCheckResponse checkScheduleConflicts(User user, ScheduleConflictCheckRequest req) {
        List<List<Long>> combinations = normalizeCombinations(
                req != null ? req.getCombinations() : null,
                req != null ? req.getSectionIds() : null,
                "Vui lòng cung cấp danh sách lớp học phần hoặc các tổ hợp cần kiểm tra.");

        Long expectedSemesterId = null;
        if (req != null && req.getPlanId() != null) {
            RegistrationPlan plan = planService.findOwnedPlan(user, req.getPlanId());
            expectedSemesterId = plan.getSemester().getId();
        }

        Set<Long> allSectionIds = new LinkedHashSet<>();
        for (List<Long> combo : combinations) {
            if (combo == null || combo.isEmpty()) {
                throw new BusinessException("Tổ hợp kiểm tra không được rỗng.");
            }
            allSectionIds.addAll(combo);
        }

        List<CourseSection> loadedSections = courseSectionRepository.findAllById(allSectionIds);
        Map<Long, CourseSection> sectionMap = loadedSections.stream()
                .collect(Collectors.toMap(CourseSection::getId, s -> s));

        for (Long sid : allSectionIds) {
            CourseSection sec = sectionMap.get(sid);
            if (sec == null) {
                throw new BusinessException("Không tìm thấy lớp học phần ID=" + sid, HttpStatus.NOT_FOUND);
            }
            if (expectedSemesterId != null && !sec.getSemester().getId().equals(expectedSemesterId)) {
                throw new BusinessException("Lớp học phần " + sec.getSectionCode() + " không thuộc học kỳ của kế hoạch.");
            }
        }

        List<SectionMeeting> allMeetings = sectionMeetingRepository.findByCourseSectionIdIn(new ArrayList<>(allSectionIds));
        Map<Long, List<SectionMeeting>> meetingMap = allMeetings.stream()
                .collect(Collectors.groupingBy(m -> m.getCourseSection().getId()));

        List<CheckedSectionCombination> validCombos = new ArrayList<>();
        List<CheckedSectionCombination> rejectedCombos = new ArrayList<>();

        for (int idx = 0; idx < combinations.size(); idx++) {
            List<Long> comboIds = combinations.get(idx);
            List<CourseSection> comboSections = comboIds.stream().map(sectionMap::get).toList();
            List<String> sectionCodes = comboSections.stream().map(CourseSection::getSectionCode).toList();

            List<ScheduleConflictDetail> conflicts = detectConflicts(comboSections, meetingMap);
            CheckedSectionCombination checked = CheckedSectionCombination.builder()
                    .combinationIndex(idx + 1)
                    .sectionIds(comboIds)
                    .sectionCodes(sectionCodes)
                    .valid(conflicts.isEmpty())
                    .conflicts(conflicts)
                    .build();

            if (conflicts.isEmpty()) {
                validCombos.add(checked);
            } else {
                rejectedCombos.add(checked);
            }
        }

        return ScheduleConflictCheckResponse.builder()
                .totalChecked(combinations.size())
                .validCount(validCombos.size())
                .rejectedCount(rejectedCombos.size())
                .validCombinations(validCombos)
                .rejectedCombinations(rejectedCombos)
                .build();
    }

    private List<ScheduleConflictDetail> detectConflicts(
            List<CourseSection> sections,
            Map<Long, List<SectionMeeting>> meetingMap) {
        List<ScheduleConflictDetail> conflicts = new ArrayList<>();

        for (int i = 0; i < sections.size(); i++) {
            CourseSection a = sections.get(i);
            for (int j = i + 1; j < sections.size(); j++) {
                CourseSection b = sections.get(j);

                if (a.getId().equals(b.getId())) {
                    conflicts.add(ScheduleConflictDetail.builder()
                            .sectionAId(a.getId())
                            .sectionACode(a.getSectionCode())
                            .courseACode(a.getCourse().getCourseCode())
                            .sectionBId(b.getId())
                            .sectionBCode(b.getSectionCode())
                            .courseBCode(b.getCourse().getCourseCode())
                            .reason("Lớp học phần " + a.getSectionCode() + " bị chọn trùng trong cùng tổ hợp.")
                            .build());
                    continue;
                }

                if (a.getCourse().getId().equals(b.getCourse().getId())) {
                    conflicts.add(ScheduleConflictDetail.builder()
                            .sectionAId(a.getId())
                            .sectionACode(a.getSectionCode())
                            .courseACode(a.getCourse().getCourseCode())
                            .sectionBId(b.getId())
                            .sectionBCode(b.getSectionCode())
                            .courseBCode(b.getCourse().getCourseCode())
                            .reason("Hai lớp " + a.getSectionCode() + " và " + b.getSectionCode()
                                    + " thuộc cùng học phần " + a.getCourse().getCourseCode() + ".")
                            .build());
                }

                List<SectionMeeting> meetingsA = meetingMap.getOrDefault(a.getId(), List.of());
                List<SectionMeeting> meetingsB = meetingMap.getOrDefault(b.getId(), List.of());

                for (SectionMeeting ma : meetingsA) {
                    for (SectionMeeting mb : meetingsB) {
                        if (meetingsOverlap(ma, mb)) {
                            conflicts.add(ScheduleConflictDetail.builder()
                                    .sectionAId(a.getId())
                                    .sectionACode(a.getSectionCode())
                                    .courseACode(a.getCourse().getCourseCode())
                                    .sectionBId(b.getId())
                                    .sectionBCode(b.getSectionCode())
                                    .courseBCode(b.getCourse().getCourseCode())
                                    .weekday(ma.getWeekday())
                                    .sectionAPeriods("Tiết " + ma.getStartPeriod() + "-" + ma.getEndPeriod())
                                    .sectionBPeriods("Tiết " + mb.getStartPeriod() + "-" + mb.getEndPeriod())
                                    .reason("Trùng lịch Thứ " + ma.getWeekday()
                                            + " giữa " + a.getSectionCode() + " (tiết " + ma.getStartPeriod() + "-" + ma.getEndPeriod() + ")"
                                            + " và " + b.getSectionCode() + " (tiết " + mb.getStartPeriod() + "-" + mb.getEndPeriod() + ").")
                                    .build());
                        }
                    }
                }
            }
        }
        return conflicts;
    }

    private boolean meetingsOverlap(SectionMeeting a, SectionMeeting b) {
        if (!Objects.equals(a.getWeekday(), b.getWeekday())) {
            return false;
        }
        if (a.getStartPeriod() != null && a.getEndPeriod() != null
                && b.getStartPeriod() != null && b.getEndPeriod() != null) {
            return a.getStartPeriod() <= b.getEndPeriod() && b.getStartPeriod() <= a.getEndPeriod();
        }
        if (a.getStartTime() != null && a.getEndTime() != null
                && b.getStartTime() != null && b.getEndTime() != null) {
            return a.getStartTime().isBefore(b.getEndTime()) && b.getStartTime().isBefore(a.getEndTime());
        }
        return false;
    }

    // ---------------- PB27: Prerequisite checking ----------------

    @Transactional(readOnly = true)
    public PrerequisiteCheckResponse checkPrerequisites(User user, PrerequisiteCheckRequest req) {
        List<Course> targetCourses;
        Long planId = req != null ? req.getPlanId() : null;

        if (planId != null) {
            RegistrationPlan plan = planService.findOwnedPlan(user, planId);
            if (req.getCourseIds() != null && !req.getCourseIds().isEmpty()) {
                targetCourses = loadCoursesOrdered(req.getCourseIds());
            } else {
                targetCourses = plan.getPlanCourses().stream()
                        .map(RegistrationPlanCourse::getCourse)
                        .toList();
            }
        } else if (req != null && req.getCourseIds() != null && !req.getCourseIds().isEmpty()) {
            targetCourses = loadCoursesOrdered(req.getCourseIds());
        } else {
            throw new BusinessException("Vui lòng cung cấp planId hoặc danh sách courseIds để kiểm tra điều kiện tiên quyết.");
        }

        if (targetCourses.isEmpty()) {
            return PrerequisiteCheckResponse.builder()
                    .planId(planId)
                    .totalChecked(0)
                    .eligibleCount(0)
                    .ineligibleCount(0)
                    .eligibleCourses(List.of())
                    .ineligibleCourses(List.of())
                    .build();
        }

        List<Long> targetIds = targetCourses.stream().map(Course::getId).toList();
        Map<Long, List<CoursePrerequisite>> prereqMap = prerequisiteRepository.findByCourseIdIn(targetIds).stream()
                .collect(Collectors.groupingBy(cp -> cp.getCourse().getId()));

        Set<Long> passedCourseIds = studentCourseRecordRepository
                .findByUserIdAndResultStatusIgnoreCase(user.getId(), "PASSED").stream()
                .map(r -> r.getCourse().getId())
                .collect(Collectors.toSet());

        List<CoursePrerequisiteStatus> eligible = new ArrayList<>();
        List<CoursePrerequisiteStatus> ineligible = new ArrayList<>();

        for (Course course : targetCourses) {
            List<CoursePrerequisite> required = prereqMap.getOrDefault(course.getId(), List.of());
            List<MissingPrerequisiteDto> missing = new ArrayList<>();
            for (CoursePrerequisite cp : required) {
                Course prereqCourse = cp.getPrerequisiteCourse();
                if (!passedCourseIds.contains(prereqCourse.getId())) {
                    missing.add(MissingPrerequisiteDto.builder()
                            .courseId(prereqCourse.getId())
                            .courseCode(prereqCourse.getCourseCode())
                            .courseName(prereqCourse.getCourseName())
                            .build());
                }
            }

            if (missing.isEmpty()) {
                eligible.add(CoursePrerequisiteStatus.builder()
                        .courseId(course.getId())
                        .courseCode(course.getCourseCode())
                        .courseName(course.getCourseName())
                        .credits(course.getCredits())
                        .eligible(true)
                        .missingPrerequisites(List.of())
                        .reason("Đáp ứng đầy đủ điều kiện tiên quyết.")
                        .build());
            } else {
                String missingNames = missing.stream()
                        .map(m -> m.getCourseCode() + " (" + m.getCourseName() + ")")
                        .collect(Collectors.joining(", "));
                ineligible.add(CoursePrerequisiteStatus.builder()
                        .courseId(course.getId())
                        .courseCode(course.getCourseCode())
                        .courseName(course.getCourseName())
                        .credits(course.getCredits())
                        .eligible(false)
                        .missingPrerequisites(missing)
                        .reason("Chưa đủ điều kiện đăng ký " + course.getCourseName()
                                + " do chưa đạt học phần tiên quyết: " + missingNames + ".")
                        .build());
            }
        }

        return PrerequisiteCheckResponse.builder()
                .planId(planId)
                .totalChecked(targetCourses.size())
                .eligibleCount(eligible.size())
                .ineligibleCount(ineligible.size())
                .eligibleCourses(eligible)
                .ineligibleCourses(ineligible)
                .build();
    }

    // ---------------- PB28: Credit limit checking ----------------

    @Transactional(readOnly = true)
    public CreditLimitCheckResponse checkCreditLimits(User user, CreditLimitCheckRequest req) {
        if (req == null) {
            throw new BusinessException("Dữ liệu kiểm tra giới hạn tín chỉ không được rỗng.");
        }
        RegistrationPlan plan = resolvePlanForCreditCheck(user, req.getPlanId(), req.getSemesterId());
        RegistrationPreference pref = preferenceRepository.findByRegistrationPlanId(plan.getId()).orElse(null);

        if (pref == null || pref.getMaxCredits() == null || pref.getMaxCredits().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Kế hoạch đăng ký chưa được thiết lập giới hạn tín chỉ (PB24).");
        }

        BigDecimal minCredits = pref.getMinCredits();
        BigDecimal maxCredits = pref.getMaxCredits();

        boolean useCourseCombos = req.getCourseCombinations() != null && !req.getCourseCombinations().isEmpty();
        List<List<Long>> combos = useCourseCombos
                ? req.getCourseCombinations()
                : normalizeCombinations(
                        req.getCombinations(),
                        req.getSectionIds(),
                        "Vui lòng cung cấp danh sách tổ hợp học phần hoặc lớp học phần để kiểm tra tín chỉ.");

        List<CheckedCreditCombination> validCombos = new ArrayList<>();
        List<CheckedCreditCombination> rejectedCombos = new ArrayList<>();

        if (useCourseCombos) {
            Set<Long> allCourseIds = combos.stream().flatMap(List::stream).collect(Collectors.toSet());
            Map<Long, Course> courseMap = courseRepository.findAllById(allCourseIds).stream()
                    .collect(Collectors.toMap(Course::getId, c -> c));
            for (Long cid : allCourseIds) {
                if (!courseMap.containsKey(cid)) {
                    throw new BusinessException("Không tìm thấy học phần ID=" + cid, HttpStatus.NOT_FOUND);
                }
            }

            for (int i = 0; i < combos.size(); i++) {
                List<Long> cIds = combos.get(i);
                Map<Long, Course> distinctCourses = new LinkedHashMap<>();
                for (Long cid : cIds) {
                    distinctCourses.put(cid, courseMap.get(cid));
                }
                BigDecimal total = distinctCourses.values().stream()
                        .map(Course::getCredits)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                evaluateCreditCombination(
                        i + 1, null, new ArrayList<>(distinctCourses.keySet()),
                        total, minCredits, maxCredits, validCombos, rejectedCombos);
            }
        } else {
            Set<Long> allSectionIds = combos.stream().flatMap(List::stream).collect(Collectors.toSet());
            Map<Long, CourseSection> sectionMap = courseSectionRepository.findAllById(allSectionIds).stream()
                    .collect(Collectors.toMap(CourseSection::getId, s -> s));
            for (Long sid : allSectionIds) {
                CourseSection sec = sectionMap.get(sid);
                if (sec == null) {
                    throw new BusinessException("Không tìm thấy lớp học phần ID=" + sid, HttpStatus.NOT_FOUND);
                }
                if (!sec.getSemester().getId().equals(plan.getSemester().getId())) {
                    throw new BusinessException("Lớp học phần " + sec.getSectionCode()
                            + " không thuộc học kỳ của kế hoạch đăng ký.");
                }
            }

            for (int i = 0; i < combos.size(); i++) {
                List<Long> sIds = combos.get(i);
                Map<Long, Course> distinctCourses = new LinkedHashMap<>();
                for (Long sid : sIds) {
                    Course c = sectionMap.get(sid).getCourse();
                    distinctCourses.put(c.getId(), c);
                }
                BigDecimal total = distinctCourses.values().stream()
                        .map(Course::getCredits)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                evaluateCreditCombination(
                        i + 1, sIds, new ArrayList<>(distinctCourses.keySet()),
                        total, minCredits, maxCredits, validCombos, rejectedCombos);
            }
        }

        return CreditLimitCheckResponse.builder()
                .planId(plan.getId())
                .minCredits(minCredits)
                .maxCredits(maxCredits)
                .totalChecked(combos.size())
                .validCount(validCombos.size())
                .rejectedCount(rejectedCombos.size())
                .validCombinations(validCombos)
                .rejectedCombinations(rejectedCombos)
                .build();
    }

    private void evaluateCreditCombination(
            int index,
            List<Long> sectionIds,
            List<Long> courseIds,
            BigDecimal totalCredits,
            BigDecimal minCredits,
            BigDecimal maxCredits,
            List<CheckedCreditCombination> validCombos,
            List<CheckedCreditCombination> rejectedCombos) {
        if (totalCredits.compareTo(minCredits) < 0) {
            rejectedCombos.add(CheckedCreditCombination.builder()
                    .combinationIndex(index)
                    .sectionIds(sectionIds)
                    .courseIds(courseIds)
                    .totalCredits(totalCredits)
                    .passed(false)
                    .violationType("BELOW_MIN_CREDITS")
                    .reason("Tổng tín chỉ (" + totalCredits + ") thấp hơn mức tối thiểu (" + minCredits + ").")
                    .build());
        } else if (totalCredits.compareTo(maxCredits) > 0) {
            rejectedCombos.add(CheckedCreditCombination.builder()
                    .combinationIndex(index)
                    .sectionIds(sectionIds)
                    .courseIds(courseIds)
                    .totalCredits(totalCredits)
                    .passed(false)
                    .violationType("EXCEEDS_MAX_CREDITS")
                    .reason("Tổng tín chỉ (" + totalCredits + ") vượt quá mức tối đa (" + maxCredits + ").")
                    .build());
        } else {
            validCombos.add(CheckedCreditCombination.builder()
                    .combinationIndex(index)
                    .sectionIds(sectionIds)
                    .courseIds(courseIds)
                    .totalCredits(totalCredits)
                    .passed(true)
                    .reason("Tổng tín chỉ (" + totalCredits + ") nằm trong giới hạn cho phép.")
                    .build());
        }
    }

    private RegistrationPlan resolvePlanForCreditCheck(User user, Long planId, Long semesterId) {
        if (planId != null) {
            return planService.findOwnedPlan(user, planId);
        }
        if (semesterId != null) {
            return planRepository.findByUserIdAndSemesterIdOrderByCreatedAtDesc(user.getId(), semesterId).stream()
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(
                            "Không tìm thấy kế hoạch đăng ký cho học kỳ này.", HttpStatus.NOT_FOUND));
        }
        throw new BusinessException("Vui lòng cung cấp planId hoặc semesterId để lấy giới hạn tín chỉ.");
    }

    private List<Course> loadCoursesOrdered(List<Long> courseIds) {
        Set<Long> uniqueIds = new LinkedHashSet<>(courseIds);
        Map<Long, Course> map = courseRepository.findAllById(uniqueIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c));
        List<Course> result = new ArrayList<>();
        for (Long id : uniqueIds) {
            Course c = map.get(id);
            if (c == null) {
                throw new BusinessException("Không tìm thấy học phần ID=" + id, HttpStatus.NOT_FOUND);
            }
            result.add(c);
        }
        return result;
    }

    private List<List<Long>> normalizeCombinations(
            List<List<Long>> combinations,
            List<Long> singleIds,
            String emptyMsg) {
        if (combinations != null && !combinations.isEmpty()) {
            return combinations;
        }
        if (singleIds != null && !singleIds.isEmpty()) {
            return List.of(singleIds);
        }
        throw new BusinessException(emptyMsg);
    }
}
