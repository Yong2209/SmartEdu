package com.smartedu.modules.system_monitoring.dto;

import java.time.LocalDateTime;

public final class SystemLogDtos {

    private SystemLogDtos() {
    }

    public record ActivityLogResponse(
            Long id,
            String actorName,
            String actionCode,
            String entityType,
            Long entityId,
            String detail,
            String ipAddress,
            LocalDateTime createdAt
    ) {
    }

    public record AiLogResponse(
        Long id,
        String operationType,
        String provider,
        String modelName,
        Long responseTimeMs,
        Long promptTokens,
        Long completionTokens,
        Long totalTokens,
        String callStatus,
        String errorCode,
        String errorMessage,
        LocalDateTime calledAt
    ) {
    }
}
