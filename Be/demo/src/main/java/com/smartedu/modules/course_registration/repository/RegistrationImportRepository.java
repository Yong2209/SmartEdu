package com.smartedu.modules.course_registration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.course_registration.entity.RegistrationImport;

public interface RegistrationImportRepository extends JpaRepository<RegistrationImport, Long> {
    List<RegistrationImport> findByUserIdOrderByCreatedAtDesc(Long userId);
}
