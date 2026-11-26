package com.smartedu.modules.schedule_management.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class SavedScheduleDtos {

    private SavedScheduleDtos() {
    }

    public record SaveRequest(
            @NotNull Long optionId,
            @NotBlank String displayName
    ) {
    }

    public record RenameRequest(
            @NotBlank String displayName
    ) {
    }

    public record SavedScheduleResponse(
            Long id,
            Long optionId,
            String optionName,
            String displayName,
            String savedAt
    ) {
    }
}
