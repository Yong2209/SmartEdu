package com.smartedu.modules.schedule_management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.schedule_management.entity.ScheduleOptionWarning;

public interface ScheduleOptionWarningRepository
        extends JpaRepository<ScheduleOptionWarning, Long> {

    List<ScheduleOptionWarning> findByScheduleOptionId(Long optionId);
}