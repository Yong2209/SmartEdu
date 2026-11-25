package com.smartedu.modules.academic_management.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smartedu.common.api.ApiResponse;
import com.smartedu.modules.academic_management.dto.AcademicDtos.CourseDto;
import com.smartedu.modules.academic_management.dto.AcademicDtos.CourseSectionDto;
import com.smartedu.modules.academic_management.dto.AcademicDtos.SemesterDto;
import com.smartedu.modules.academic_management.service.AcademicCatalogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/academic")
@RequiredArgsConstructor
public class AcademicCatalogController {

    private final AcademicCatalogService academicCatalogService;

    @GetMapping("/semesters")
    public ApiResponse<List<SemesterDto>> listSemesters() {
        return ApiResponse.success(academicCatalogService.listSemesters());
    }

    @GetMapping("/courses")
    public ApiResponse<List<CourseDto>> searchCourses(@RequestParam(required = false) String keyword) {
        return ApiResponse.success(academicCatalogService.searchCatalogCourses(keyword));
    }

    @GetMapping("/semesters/{semesterId}/courses")
    public ApiResponse<List<CourseDto>> getCoursesInSemester(
            @PathVariable Long semesterId,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(academicCatalogService.getCoursesInSemester(semesterId, keyword));
    }

    @GetMapping("/semesters/{semesterId}/sections")
    public ApiResponse<List<CourseSectionDto>> getSectionsInSemester(
            @PathVariable Long semesterId,
            @RequestParam(required = false) Long courseId) {
        return ApiResponse.success(academicCatalogService.getSectionsInSemester(semesterId, courseId));
    }
}
