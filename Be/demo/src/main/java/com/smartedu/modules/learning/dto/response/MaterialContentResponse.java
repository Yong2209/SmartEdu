package com.smartedu.modules.learning.dto.response;

public record MaterialContentResponse(
    Long materialId,
    String title,
    String extractionStatus,
    String extractedText
) {}

