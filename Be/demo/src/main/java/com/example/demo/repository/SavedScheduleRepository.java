package com.example.demo.repository;
import com.example.demo.entity.SavedSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface SavedScheduleRepository extends JpaRepository<SavedSchedule, Long> {
    List<SavedSchedule> findByUserIdOrderBySavedAtDesc(Long userId);
    long countByUserIdAndScheduleOptionScheduleGenerationRegistrationPlanSemesterId(Long userId, Long semesterId);
}
