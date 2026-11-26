package com.smartedu.modules.schedule_management.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.common.security.CurrentUserResolver;
import com.smartedu.modules.schedule_management.dto.ScheduleEvaluationDtos;
import com.smartedu.modules.schedule_management.entity.ScheduleOption;
import com.smartedu.modules.schedule_management.repository.ScheduleOptionEvaluationRepository;
import com.smartedu.modules.schedule_management.repository.ScheduleOptionRepository;
import com.smartedu.modules.schedule_management.repository.ScheduleOptionWarningRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ScheduleEvaluationService {

    private final ScheduleOptionRepository optionRepository;
    private final ScheduleOptionEvaluationRepository evaluationRepository;
    private final ScheduleOptionWarningRepository warningRepository;
    private final CurrentUserResolver currentUserResolver;

    @Transactional(readOnly = true)
    public ScheduleEvaluationDtos.EvaluationResponse getEvaluation(
            Long optionId
    ) {
        Long userId = currentUserResolver.requireCurrentUser().getId();

        ScheduleOption option = optionRepository
                .findByIdAndScheduleGenerationRegistrationPlanUserId(
                        optionId,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy phương án thời khóa biểu.",
                        HttpStatus.NOT_FOUND
                ));

        List<ScheduleEvaluationDtos.EvaluationItem> evaluations =
                evaluationRepository.findByScheduleOptionId(optionId)
                        .stream()
                        .map(e -> new ScheduleEvaluationDtos.EvaluationItem(
                                e.getCriterionCode(),
                                e.getCriterionName(),
                                e.getScoreValue(),
                                e.getWeightValue(),
                                e.getExplanation()
                        ))
                        .toList();

        List<ScheduleEvaluationDtos.WarningItem> warnings =
                warningRepository.findByScheduleOptionId(optionId)
                        .stream()
                        .map(w -> new ScheduleEvaluationDtos.WarningItem(
                                w.getWarningType(),
                                w.getSeverity(),
                                w.getWarningMessage()
                        ))
                        .toList();

        return new ScheduleEvaluationDtos.EvaluationResponse(
                option.getId(),
                option.getOptionName(),
                option.getOptionRank(),
                option.getTotalScore(),
                evaluations,
                warnings
        );
    }
}
