package com.smartedu.modules.schedule_management.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.schedule_management.entity.ScheduleExport;

public interface ScheduleExportRepository
        extends JpaRepository<ScheduleExport, Long> {
}
