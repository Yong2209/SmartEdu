package com.smartedu.modules.learning.dto.response;

import java.time.LocalDateTime;

public record QuestionGenerationResponse(
    Long requestId,
    Long materialId,
    String contentScope,
    String questionType,
    Integer questionCount,
    String difficulty,
    String status,
    LocalDateTime requestedAt
) {}
