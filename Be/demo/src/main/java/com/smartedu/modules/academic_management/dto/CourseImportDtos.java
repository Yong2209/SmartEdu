package com.smartedu.modules.academic_management.dto;

import java.util.List;

public final class CourseImportDtos {

    private CourseImportDtos() {
    }

    public record ImportRowResult(
            Integer rowNumber,
            String courseCode,
            String sectionCode,
            boolean success,
            String message
    ) {
    }

    public record ImportResponse(
            int totalRows,
            int successRows,
            int errorRows,
            List<ImportRowResult> rows
    ) {
    }
}
