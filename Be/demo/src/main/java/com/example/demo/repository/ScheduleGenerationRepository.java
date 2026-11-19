package com.example.demo.repository;
import com.example.demo.entity.ScheduleGeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ScheduleGenerationRepository extends JpaRepository<ScheduleGeneration, Long> {
    List<ScheduleGeneration> findByRegistrationPlanIdOrderByGenerationNoDesc(Long registrationPlanId);
}
