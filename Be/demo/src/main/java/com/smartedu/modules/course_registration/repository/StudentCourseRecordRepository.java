package com.smartedu.modules.course_registration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.course_registration.entity.StudentCourseRecord;

public interface StudentCourseRecordRepository extends JpaRepository<StudentCourseRecord, Long> {
    List<StudentCourseRecord> findByUserIdOrderByCompletedAtDescIdDesc(Long userId);
    List<StudentCourseRecord> findByUserIdAndResultStatusIgnoreCase(Long userId, String resultStatus);
    Optional<StudentCourseRecord> findByUserIdAndCourseId(Long userId, Long courseId);
}
