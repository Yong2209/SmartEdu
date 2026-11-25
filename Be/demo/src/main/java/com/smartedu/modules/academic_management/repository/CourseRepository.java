package com.smartedu.modules.academic_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smartedu.modules.academic_management.entity.Course;

public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByCourseCodeIgnoreCase(String courseCode);

    List<Course> findByIsActiveTrueOrderByCourseCodeAsc();

    @Query("""
            SELECT c FROM Course c
            WHERE c.isActive = true
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(c.courseCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(c.courseName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY c.courseCode ASC
            """)
    List<Course> searchActiveCourses(@Param("keyword") String keyword);

    @Query("""
            SELECT DISTINCT cs.course FROM CourseSection cs
            WHERE cs.semester.id = :semesterId
              AND cs.course.isActive = true
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(cs.course.courseCode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(cs.course.courseName) LIKE LOWER(CONCAT('%', :keyword, '%')))
            ORDER BY cs.course.courseCode ASC
            """)
    List<Course> findOfferedCoursesInSemester(
            @Param("semesterId") Long semesterId,
            @Param("keyword") String keyword);
}
