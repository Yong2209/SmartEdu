package com.smartedu.modules.schedule_management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.schedule_management.entity.ScheduleOptionSection;

public interface ScheduleOptionSectionRepository
        extends JpaRepository<ScheduleOptionSection, Long> {

    List<ScheduleOptionSection> findByScheduleOptionId(Long optionId);

    boolean existsByScheduleOptionIdAndCourseSectionId(
            Long optionId,
            Long sectionId
    );
}