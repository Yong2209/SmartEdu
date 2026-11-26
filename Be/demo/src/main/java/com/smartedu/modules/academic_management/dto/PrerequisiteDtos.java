package com.smartedu.modules.academic_management.dto;

import jakarta.validation.constraints.NotNull;

public final class PrerequisiteDtos {

    private PrerequisiteDtos() {
    }

    public record CreateRequest(
            @NotNull Long courseId,
            @NotNull Long prerequisiteCourseId
    ) {
    }

    public record PrerequisiteResponse(
            Long id,
            Long courseId,
            String courseCode,
            String courseName,
            Long prerequisiteCourseId,
            String prerequisiteCourseCode,
            String prerequisiteCourseName
    ) {
    }
}
