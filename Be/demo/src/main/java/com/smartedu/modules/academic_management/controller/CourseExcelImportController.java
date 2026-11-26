package com.smartedu.modules.academic_management.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.academic_management.dto.CourseImportDtos;
import com.smartedu.modules.academic_management.service.CourseExcelImportService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin/academic")
@RequiredArgsConstructor
public class CourseExcelImportController {

    private final CourseExcelImportService importService;

    @PostMapping(
            value = "/courses/import",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ApiResponse<CourseImportDtos.ImportResponse> importExcel(
            @RequestParam("file") MultipartFile file
    ) {
        return ApiResponse.success(
                importService.importCourses(file),
                "Đã xử lý file Excel."
        );
    }
}
