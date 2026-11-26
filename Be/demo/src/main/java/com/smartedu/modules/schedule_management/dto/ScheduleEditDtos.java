package com.smartedu.modules.schedule_management.dto;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public final class ScheduleEditDtos {

    private ScheduleEditDtos() {
    }

    public record CompareRequest(
            @NotEmpty
            List<Long> optionIds
    ) {
    }

    public record CompareResponse(
            List<ScheduleDtos.OptionDto> options
    ) {
    }

    public record ReplaceSectionRequest(
            @NotNull Long oldSectionId,
            @NotNull Long newSectionId,
            String newOptionName
    ) {
    }
}
