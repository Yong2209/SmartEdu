package com.smartedu.modules.schedule_management.dto;

import java.math.BigDecimal;
import java.util.List;

public final class ScheduleEvaluationDtos {

    private ScheduleEvaluationDtos() {
    }

    public record EvaluationItem(
            String criterionCode,
            String criterionName,
            BigDecimal scoreValue,
            BigDecimal weightValue,
            String explanation
    ) {
    }

    public record WarningItem(
            String warningType,
            String severity,
            String warningMessage
    ) {
    }

    public record EvaluationResponse(
            Long optionId,
            String optionName,
            Integer rank,
            BigDecimal totalScore,
            List<EvaluationItem> evaluations,
            List<WarningItem> warnings
    ) {
    }
}
