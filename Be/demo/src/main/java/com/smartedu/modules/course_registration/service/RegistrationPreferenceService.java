package com.smartedu.modules.course_registration.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.CreditLimitResponse;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.SchedulePreferenceRequest;
import com.smartedu.modules.course_registration.dto.RegistrationDtos.SchedulePreferenceResponse;
import com.smartedu.modules.course_registration.entity.RegistrationPlan;
import com.smartedu.modules.course_registration.entity.RegistrationPreference;
import com.smartedu.modules.course_registration.repository.RegistrationPreferenceRepository;
import com.smartedu.modules.user_management.entity.User;

@Service
public class RegistrationPreferenceService {

    private static final Set<String> VALID_SHIFTS = Set.of("MORNING", "AFTERNOON", "EVENING", "ANY");

    private final RegistrationPlanService planService;
    private final RegistrationPreferenceRepository preferenceRepository;
    private final ObjectMapper objectMapper;
    private final BigDecimal systemMinCredits;
    private final BigDecimal systemMaxCredits;

    public RegistrationPreferenceService(
            RegistrationPlanService planService,
            RegistrationPreferenceRepository preferenceRepository,
            ObjectMapper objectMapper,
            @Value("${smartedu.registration.min-credits:#{null}}") BigDecimal systemMinCredits,
            @Value("${smartedu.registration.max-credits:#{null}}") BigDecimal systemMaxCredits) {
        this.planService = planService;
        this.preferenceRepository = preferenceRepository;
        this.objectMapper = objectMapper;
        this.systemMinCredits = systemMinCredits;
        this.systemMaxCredits = systemMaxCredits;
    }

    @Transactional(readOnly = true)
    public CreditLimitResponse getCreditLimits(User user, Long planId) {
        RegistrationPlan plan = planService.findOwnedPlan(user, planId);
        RegistrationPreference pref = preferenceRepository.findByRegistrationPlanId(plan.getId()).orElse(null);
        boolean configured = pref != null && pref.getMaxCredits() != null
                && pref.getMaxCredits().compareTo(BigDecimal.ZERO) > 0;
        return CreditLimitResponse.builder()
                .planId(plan.getId())
                .semesterId(plan.getSemester().getId())
                .minCredits(configured ? pref.getMinCredits() : null)
                .maxCredits(configured ? pref.getMaxCredits() : null)
                .configured(configured)
                .build();
    }

    @Transactional
    public CreditLimitResponse saveCreditLimits(User user, Long planId, CreditLimitRequest req) {
        RegistrationPlan plan = planService.findOwnedPlan(user, planId);

        BigDecimal min = req.getMinCredits();
        BigDecimal max = req.getMaxCredits();
        if (min == null || max == null) {
            throw new BusinessException("Vui lòng nhập đầy đủ số tín chỉ tối thiểu và tối đa.");
        }
        if (min.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("Số tín chỉ tối thiểu không được nhỏ hơn 0.");
        }
        if (max.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Số tín chỉ tối đa phải lớn hơn 0.");
        }
        if (min.compareTo(max) > 0) {
            throw new BusinessException("Số tín chỉ tối thiểu không được lớn hơn số tín chỉ tối đa.");
        }
        if (systemMinCredits != null && systemMinCredits.compareTo(BigDecimal.ZERO) > 0
                && min.compareTo(systemMinCredits) < 0) {
            throw new BusinessException("Số tín chỉ tối thiểu không được thấp hơn quy định đào tạo (" + systemMinCredits + ").");
        }
        if (systemMaxCredits != null && systemMaxCredits.compareTo(BigDecimal.ZERO) > 0
                && max.compareTo(systemMaxCredits) > 0) {
            throw new BusinessException("Số tín chỉ tối đa không được vượt quá quy định đào tạo (" + systemMaxCredits + ").");
        }

        RegistrationPreference pref = preferenceRepository.findByRegistrationPlanId(plan.getId())
                .orElseGet(() -> RegistrationPreference.builder()
                        .registrationPlan(plan)
                        .avoidSaturday(false)
                        .compactDays(false)
                        .minimizeGaps(false)
                        .build());

        pref.setMinCredits(min);
        pref.setMaxCredits(max);
        pref = preferenceRepository.save(pref);

        return CreditLimitResponse.builder()
                .planId(plan.getId())
                .semesterId(plan.getSemester().getId())
                .minCredits(pref.getMinCredits())
                .maxCredits(pref.getMaxCredits())
                .configured(true)
                .build();
    }

    @Transactional(readOnly = true)
    public SchedulePreferenceResponse getSchedulePreferences(User user, Long planId) {
        RegistrationPlan plan = planService.findOwnedPlan(user, planId);
        RegistrationPreference pref = preferenceRepository.findByRegistrationPlanId(plan.getId()).orElse(null);
        return toSchedulePreferenceResponse(plan, pref);
    }

    @Transactional
    public SchedulePreferenceResponse saveSchedulePreferences(User user, Long planId, SchedulePreferenceRequest req) {
        RegistrationPlan plan = planService.findOwnedPlan(user, planId);

        String shift = null;
        if (req.getPreferredShift() != null && !req.getPreferredShift().isBlank()) {
            shift = req.getPreferredShift().trim().toUpperCase(Locale.ROOT);
            if (!VALID_SHIFTS.contains(shift)) {
                throw new BusinessException("Ca học ưu tiên không hợp lệ. Chỉ hỗ trợ MORNING, AFTERNOON, EVENING hoặc ANY.");
            }
        }

        List<Integer> freeDays = List.of();
        if (req.getPreferredFreeDays() != null && !req.getPreferredFreeDays().isEmpty()) {
            for (Integer d : req.getPreferredFreeDays()) {
                if (d == null || d < 1 || d > 7) {
                    throw new BusinessException("Ngày nghỉ ưu tiên phải là giá trị thứ từ 1 đến 7.");
                }
            }
            freeDays = req.getPreferredFreeDays().stream().distinct().sorted().toList();
        }

        if (req.getMaxGapPeriods() != null && (req.getMaxGapPeriods() < 0 || req.getMaxGapPeriods() > 12)) {
            throw new BusinessException("Số tiết trống tối đa phải nằm trong khoảng từ 0 đến 12.");
        }

        RegistrationPreference pref = preferenceRepository.findByRegistrationPlanId(plan.getId())
                .orElseGet(() -> RegistrationPreference.builder()
                        .registrationPlan(plan)
                        .minCredits(BigDecimal.ZERO)
                        .maxCredits(BigDecimal.ZERO)
                        .build());

        pref.setPreferredShift(shift);
        pref.setAvoidSaturday(Boolean.TRUE.equals(req.getAvoidSaturday()));
        pref.setCompactDays(Boolean.TRUE.equals(req.getCompactDays()));
        pref.setMinimizeGaps(Boolean.TRUE.equals(req.getMinimizeGaps()));
        pref.setMaxGapPeriods(req.getMaxGapPeriods());
        pref.setPreferredFreeDays(freeDays.isEmpty() ? null : writeJson(freeDays));

        pref = preferenceRepository.save(pref);
        return toSchedulePreferenceResponse(plan, pref);
    }

    private SchedulePreferenceResponse toSchedulePreferenceResponse(RegistrationPlan plan, RegistrationPreference pref) {
        if (pref == null) {
            return SchedulePreferenceResponse.builder()
                    .planId(plan.getId())
                    .semesterId(plan.getSemester().getId())
                    .preferredShift(null)
                    .avoidSaturday(false)
                    .compactDays(false)
                    .minimizeGaps(false)
                    .preferredFreeDays(List.of())
                    .maxGapPeriods(null)
                    .constraintType("SOFT_CONSTRAINT")
                    .build();
        }
        return SchedulePreferenceResponse.builder()
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

    private String writeJson(List<Integer> days) {
        try {
            return objectMapper.writeValueAsString(days);
        } catch (Exception e) {
            throw new BusinessException("Không thể lưu danh sách ngày nghỉ ưu tiên.");
        }
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
