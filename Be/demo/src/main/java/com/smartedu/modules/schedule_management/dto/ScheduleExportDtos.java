package com.smartedu.modules.schedule_management.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public final class ScheduleExportDtos {

    private ScheduleExportDtos() {
    }

    public record ExportRequest(
            @NotNull Long optionId,

            @NotNull
            @Pattern(regexp = "(?i)PDF|PNG")
            String format
    ) {
    }
}
