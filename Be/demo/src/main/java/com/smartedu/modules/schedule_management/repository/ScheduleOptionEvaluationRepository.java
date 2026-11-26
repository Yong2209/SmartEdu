package com.smartedu.modules.schedule_management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.schedule_management.entity.ScheduleOptionEvaluation;

public interface ScheduleOptionEvaluationRepository
        extends JpaRepository<ScheduleOptionEvaluation, Long> {

    List<ScheduleOptionEvaluation> findByScheduleOptionId(Long optionId);
}