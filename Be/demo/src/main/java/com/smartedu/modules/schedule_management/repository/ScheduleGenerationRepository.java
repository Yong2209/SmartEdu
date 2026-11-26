package com.smartedu.modules.schedule_management.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.schedule_management.entity.ScheduleGeneration;

public interface ScheduleGenerationRepository
        extends JpaRepository<ScheduleGeneration, Long> {

    Optional<ScheduleGeneration> findTopByRegistrationPlanIdOrderByGenerationNoDesc(
            Long registrationPlanId
    );
}