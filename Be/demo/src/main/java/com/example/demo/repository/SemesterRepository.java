package com.example.demo.repository;
import com.example.demo.entity.Semester;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findBySemesterCode(String semesterCode);
}
