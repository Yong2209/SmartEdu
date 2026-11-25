package com.smartedu.modules.course_registration.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.academic_management.entity.Course;
import com.smartedu.modules.academic_management.entity.Semester;
import com.smartedu.modules.academic_management.repository.CourseRepository;
import com.smartedu.modules.academic_management.repository.CourseSectionRepository;
import com.smartedu.modules.academic_management.repository.SemesterRepository;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreatePlanRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.PlanCourseDto;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.RegistrationPlanResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.SchedulePreferenceResponse;
import com.smartedu.modules.course_registration.entity.RegistrationPlan;
import com.smartedu.modules.course_registration.entity.RegistrationPlanCourse;
import com.smartedu.modules.course_registration.entity.RegistrationPreference;
import com.smartedu.modules.course_registration.repository.RegistrationPlanRepository;
import com.smartedu.modules.user_management.entity.User;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RegistrationPlanService {

    private final RegistrationPlanRepository planRepository;
    private final SemesterRepository semesterRepository;
    private final CourseRepository courseRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RegistrationPlanResponse createPlan(User user, CreatePlanRequest req) {
        Semester semester = semesterRepository.findById(req.getSemesterId())
                .orElseThrow(() -> new BusinessException("Không tìm thấy học kỳ.", HttpStatus.NOT_FOUND));

        String planName = (req.getPlanName() != null && !req.getPlanName().isBlank())
                ? req.getPlanName().trim()
                : "Kế hoạch đăng ký - " + semester.getSemesterName();

        RegistrationPlan plan = RegistrationPlan.builder()
                .user(user)
                .semester(semester)
                .planName(planName)
                .planStatus("DRAFT")
                .build();

        if (req.getCourseIds() != null && !req.getCourseIds().isEmpty()) {
            addValidatedCoursesToPlan(plan, semester.getId(), req.getCourseIds());
        }

        plan = planRepository.save(plan);
        return toPlanResponse(plan);
    }

    @Transactional(readOnly = true)
    public List<RegistrationPlanResponse> listMyPlans(User user, Long semesterId) {
        List<RegistrationPlan> plans = semesterId != null
                ? planRepository.findByUserIdAndSemesterIdOrderByCreatedAtDesc(user.getId(), semesterId)
                : planRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        return plans.stream().map(this::toPlanResponse).toList();
    }

    @Transactional(readOnly = true)
    public RegistrationPlanResponse getPlanDetail(User user, Long planId) {
        RegistrationPlan plan = findOwnedPlan(user, planId);
        return toPlanResponse(plan);
    }

    @Transactional
    public RegistrationPlanResponse replacePlanCourses(User user, Long planId, List<Long> courseIds) {
        RegistrationPlan plan = findOwnedPlan(user, planId);
        plan.getPlanCourses().clear();
        planRepository.saveAndFlush(plan);

        if (courseIds != null && !courseIds.isEmpty()) {
            addValidatedCoursesToPlan(plan, plan.getSemester().getId(), courseIds);
        }
        plan = planRepository.save(plan);
        return toPlanResponse(plan);
    }

    @Transactional
    public RegistrationPlanResponse addCourseToPlan(User user, Long planId, Long courseId) {
        RegistrationPlan plan = findOwnedPlan(user, planId);
        boolean alreadyExists = plan.getPlanCourses().stream()
                .anyMatch(pc -> pc.getCourse().getId().equals(courseId));
        if (alreadyExists) {
            throw new BusinessException("Học phần này đã có trong kế hoạch đăng ký.");
        }
        addValidatedCoursesToPlan(plan, plan.getSemester().getId(), List.of(courseId));
        plan = planRepository.save(plan);
        return toPlanResponse(plan);
    }

    @Transactional
    public RegistrationPlanResponse removeCourseFromPlan(User user, Long planId, Long courseId) {
        RegistrationPlan plan = findOwnedPlan(user, planId);
        boolean removed = plan.getPlanCourses().removeIf(pc -> pc.getCourse().getId().equals(courseId));
        if (!removed) {
            throw new BusinessException("Học phần không nằm trong kế hoạch đăng ký.", HttpStatus.NOT_FOUND);
        }
        plan = planRepository.save(plan);
        return toPlanResponse(plan);
    }

    public RegistrationPlan findOwnedPlan(User user, Long planId) {
        RegistrationPlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy kế hoạch đăng ký.", HttpStatus.NOT_FOUND));
        if (!plan.getUser().getId().equals(user.getId())) {
            throw new BusinessException("Bạn không có quyền truy cập kế hoạch đăng ký này.", HttpStatus.FORBIDDEN);
        }
        return plan;
    }

    private void addValidatedCoursesToPlan(RegistrationPlan plan, Long semesterId, List<Long> courseIds) {
        Set<Long> uniqueIds = new LinkedHashSet<>(courseIds);
        for (Long courseId : uniqueIds) {
            Course course = courseRepository.findById(courseId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy học phần ID=" + courseId, HttpStatus.NOT_FOUND));
            if (!Boolean.TRUE.equals(course.getIsActive())) {
                throw new BusinessException("Học phần " + course.getCourseCode() + " hiện không hoạt động.");
            }
            if (!courseSectionRepository.existsBySemesterIdAndCourseId(semesterId, course.getId())) {
                throw new BusinessException("Học phần " + course.getCourseCode()
                        + " không được mở lớp trong học kỳ " + plan.getSemester().getSemesterCode() + ".");
            }
            plan.getPlanCourses().add(RegistrationPlanCourse.builder()
                    .registrationPlan(plan)
                    .course(course)
                    .selectionSource("MANUAL")
                    .build());
        }
    }

    public RegistrationPlanResponse toPlanResponse(RegistrationPlan plan) {
        Map<Long, Long> openSectionCounts = courseSectionRepository
                .findBySemesterIdOrderBySectionCodeAsc(plan.getSemester().getId()).stream()
                .filter(s -> "OPEN".equalsIgnoreCase(s.getSectionStatus()))
                .collect(Collectors.groupingBy(s -> s.getCourse().getId(), Collectors.counting()));

        List<PlanCourseDto> courseDtos = new ArrayList<>();
        BigDecimal totalCredits = BigDecimal.ZERO;
        for (RegistrationPlanCourse pc : plan.getPlanCourses()) {
            Course c = pc.getCourse();
            totalCredits = totalCredits.add(c.getCredits());
            courseDtos.add(PlanCourseDto.builder()
                    .courseId(c.getId())
                    .courseCode(c.getCourseCode())
                    .courseName(c.getCourseName())
                    .credits(c.getCredits())
                    .selectionSource(pc.getSelectionSource())
                    .openSectionsCount(openSectionCounts.getOrDefault(c.getId(), 0L).intValue())
                    .build());
        }

        RegistrationPreference pref = plan.getPreference();
        CreditLimitResponse creditLimits = null;
        SchedulePreferenceResponse schedulePrefs = null;
        if (pref != null) {
            creditLimits = CreditLimitResponse.builder()
                    .planId(plan.getId())
                    .semesterId(plan.getSemester().getId())
                    .minCredits(pref.getMinCredits())
                    .maxCredits(pref.getMaxCredits())
                    .configured(pref.getMaxCredits() != null && pref.getMaxCredits().compareTo(BigDecimal.ZERO) > 0)
                    .build();
            schedulePrefs = SchedulePreferenceResponse.builder()
                    .planId(plan.getId())
                    .semesterId(plan.getSemester().getId())
                    .preferredShift(pref.getPreferredShift())
                    .avoidSaturday(pref.getAvoidSaturday())
                    .compactDays(pref.getCompactDays())
                    .minimizeGaps(pref.getMinimizeGaps())
                    .preferredFreeDays(parseFreeDays(pref.getPreferredFreeDays()))
                    .maxGapPeriods(pref.getMaxGapPeriods())
                    .constraintType("SOFT_CONSTRAINT")
                    .build();
        }

        return RegistrationPlanResponse.builder()
                .planId(plan.getId())
                .semesterId(plan.getSemester().getId())
                .semesterCode(plan.getSemester().getSemesterCode())
                .semesterName(plan.getSemester().getSemesterName())
                .sourceImportId(plan.getSourceImport() != null ? plan.getSourceImport().getId() : null)
                .planName(plan.getPlanName())
                .planStatus(plan.getPlanStatus())
                .courseCount(courseDtos.size())
                .totalCredits(totalCredits)
                .createdAt(plan.getCreatedAt())
                .updatedAt(plan.getUpdatedAt())
                .courses(courseDtos)
                .creditLimits(creditLimits)
                .schedulePreferences(schedulePrefs)
                .build();
    }

    private List<Integer> parseFreeDays(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Integer>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
