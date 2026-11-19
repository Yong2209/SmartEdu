package com.smartedu.modules.learning.dto.response;

import java.time.LocalDateTime;

public record LearningMaterialResponse(
    Long id,
    String title,
    String originalFileName,
    String fileExtension,
    Long fileSizeBytes,
    String processingStatus,
    LocalDateTime uploadedAt
) {}
