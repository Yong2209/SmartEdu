package com.smartedu.modules.academic_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.academic_management.entity.Semester;

public interface SemesterRepository extends JpaRepository<Semester, Long> {
    List<Semester> findAllByOrderByAcademicYearDescTermNoDesc();
    Optional<Semester> findBySemesterCodeIgnoreCase(String semesterCode);
}
