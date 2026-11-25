package com.smartedu.modules.academic_management.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.academic_management.entity.SectionMeeting;

public interface SectionMeetingRepository extends JpaRepository<SectionMeeting, Long> {
    List<SectionMeeting> findByCourseSectionIdOrderByWeekdayAscStartPeriodAsc(Long courseSectionId);
    List<SectionMeeting> findByCourseSectionIdIn(List<Long> courseSectionIds);
}
