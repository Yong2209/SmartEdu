package com.smartedu.modules.academic_management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.academic_management.entity.CoursePrerequisite;

public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, Long> {
    List<CoursePrerequisite> findByCourseId(Long courseId);
    List<CoursePrerequisite> findByCourseIdIn(List<Long> courseIds);
    
    boolean existsByCourseIdAndPrerequisiteCourseId(
        Long courseId,
        Long prerequisiteCourseId
    );

    void deleteByCourseIdAndPrerequisiteCourseId(
            Long courseId,
            Long prerequisiteCourseId
    );
}
