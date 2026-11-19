package com.example.demo.repository;
import com.example.demo.entity.ScheduleOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ScheduleOptionRepository extends JpaRepository<ScheduleOption, Long> {
    List<ScheduleOption> findByScheduleGenerationIdOrderByOptionRankAsc(Long scheduleGenerationId);
}
