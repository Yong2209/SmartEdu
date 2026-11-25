package com.smartedu.modules.course_registration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.course_registration.entity.RegistrationImportError;

public interface RegistrationImportErrorRepository extends JpaRepository<RegistrationImportError, Long> {
    List<RegistrationImportError> findByRegistrationImportIdOrderByRowNumberAsc(Long registrationImportId);
}
