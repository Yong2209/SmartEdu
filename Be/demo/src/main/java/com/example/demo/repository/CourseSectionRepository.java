package com.example.demo.repository;
import com.example.demo.entity.CourseSection;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface CourseSectionRepository extends JpaRepository<CourseSection, Long> {
    List<CourseSection> findBySemesterIdAndSectionStatus(Long semesterId, String sectionStatus);
    List<CourseSection> findBySemesterIdAndCourseIdInAndSectionStatus(Long semesterId, List<Long> courseIds, String sectionStatus);
    List<CourseSection> findBySemesterIdAndCourseId(Long semesterId, Long courseId);
    Optional<CourseSection> findBySemesterIdAndSectionCode(Long semesterId, String sectionCode);
}
