package com.smartedu.modules.academic_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.academic_management.entity.CourseSection;

public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {
    List<CourseSection> findBySemesterIdOrderBySectionCodeAsc(Long semesterId);
    List<CourseSection> findBySemesterIdAndCourseIdOrderBySectionCodeAsc(Long semesterId, Long courseId);
    Optional<CourseSection> findBySemesterIdAndSectionCodeIgnoreCase(Long semesterId, String sectionCode);
    boolean existsBySemesterIdAndCourseId(Long semesterId, Long courseId);
}
