package com.smartedu.modules.system_monitoring.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.modules.ai_integration.repository.AiCallLogRepository;
import com.smartedu.modules.system_monitoring.dto.SystemLogDtos;
import com.smartedu.modules.system_monitoring.repository.ActivityLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SystemLogService {

    private final ActivityLogRepository activityLogRepository;
    private final AiCallLogRepository aiCallLogRepository;

    @Transactional(readOnly = true)
    public List<SystemLogDtos.ActivityLogResponse> getActivityLogs(
            LocalDate from,
            LocalDate to
    ) {
        var logs = (from != null && to != null)
                ? activityLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                        from.atStartOfDay(),
                        to.plusDays(1).atStartOfDay().minusNanos(1)
                )
                : activityLogRepository.findAllByOrderByCreatedAtDesc();

        return logs.stream()
                .map(log -> new SystemLogDtos.ActivityLogResponse(
                        log.getId(),
                        log.getActor() == null
                                ? null
                                : log.getActor().getFullName(),
                        log.getActionCode(),
                        log.getEntityType(),
                        log.getEntityId(),
                        log.getDetail(),
                        log.getIpAddress(),
                        log.getCreatedAt()
                ))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SystemLogDtos.AiLogResponse> getAiLogs() {
        return aiCallLogRepository.findAll()
                .stream()
                .map(log -> new SystemLogDtos.AiLogResponse(
                        log.getId(),
                        log.getOperationType(),
                        log.getProvider(),
                        log.getModelName(),
                        log.getResponseTimeMs(),
                        log.getPromptTokens(),
                        log.getCompletionTokens(),
                        log.getTotalTokens(),
                        log.getCallStatus(),
                        log.getErrorCode(),
                        log.getErrorMessage(),
                        log.getCalledAt()
                ))
                .toList();
    }
}
