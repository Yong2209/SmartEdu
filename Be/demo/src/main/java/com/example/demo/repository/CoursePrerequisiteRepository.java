package com.example.demo.repository;
import com.example.demo.entity.CoursePrerequisite;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface CoursePrerequisiteRepository extends JpaRepository<CoursePrerequisite, Long> {
    List<CoursePrerequisite> findByCourseId(Long courseId);
    List<CoursePrerequisite> findByCourseIdIn(List<Long> courseIds);
    boolean existsByCourseIdAndPrerequisiteCourseId(Long courseId, Long prerequisiteCourseId);
}
