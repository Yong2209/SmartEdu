package com.smartedu.modules.schedule_management.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.schedule_management.entity.SavedSchedule;

public interface SavedScheduleRepository
        extends JpaRepository<SavedSchedule, Long> {

    List<SavedSchedule> findByUserIdOrderBySavedAtDesc(Long userId);

    Optional<SavedSchedule> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndScheduleOptionId(Long userId, Long optionId);
}
