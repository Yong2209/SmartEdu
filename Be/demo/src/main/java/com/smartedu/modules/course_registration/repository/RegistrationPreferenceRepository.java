package com.smartedu.modules.course_registration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.course_registration.entity.RegistrationPreference;

public interface RegistrationPreferenceRepository extends JpaRepository<RegistrationPreference, Long> {
    Optional<RegistrationPreference> findByRegistrationPlanId(Long registrationPlanId);
}
