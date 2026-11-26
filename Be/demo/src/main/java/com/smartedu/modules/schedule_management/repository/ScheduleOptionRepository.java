package com.smartedu.modules.schedule_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.smartedu.modules.schedule_management.entity.ScheduleOption;

public interface ScheduleOptionRepository
        extends JpaRepository<ScheduleOption, Long> {

    List<ScheduleOption> findByScheduleGenerationIdOrderByOptionRankAsc(
            Long generationId
    );

    @EntityGraph(attributePaths = {
            "sections",
            "sections.courseSection",
            "sections.courseSection.meetings",
            "sections.courseSection.course",
            "sections.courseSection.meetings.room",
            "evaluations",
            "warnings"
    })
    Optional<ScheduleOption> findByIdAndScheduleGenerationRegistrationPlanUserId(
            Long optionId,
            Long userId
    );
}
