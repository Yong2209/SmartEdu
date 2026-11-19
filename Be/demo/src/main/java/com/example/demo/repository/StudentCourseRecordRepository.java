package com.example.demo.repository;
import com.example.demo.entity.StudentCourseRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface StudentCourseRecordRepository extends JpaRepository<StudentCourseRecord, Long> {
    List<StudentCourseRecord> findByUserId(Long userId);
    List<StudentCourseRecord> findByUserIdAndResultStatus(Long userId, String resultStatus);
}
