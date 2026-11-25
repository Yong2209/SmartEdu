package com.smartedu.modules.course_registration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.course_registration.entity.RegistrationPlan;

public interface RegistrationPlanRepository extends JpaRepository<RegistrationPlan, Long> {
    List<RegistrationPlan> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<RegistrationPlan> findByUserIdAndSemesterIdOrderByCreatedAtDesc(Long userId, Long semesterId);
    Optional<RegistrationPlan> findByIdAndUserId(Long id, Long userId);
}
