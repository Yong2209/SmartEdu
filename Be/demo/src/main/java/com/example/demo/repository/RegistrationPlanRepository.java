package com.example.demo.repository;
import com.example.demo.entity.RegistrationPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface RegistrationPlanRepository extends JpaRepository<RegistrationPlan, Long> {
    List<RegistrationPlan> findByUserIdOrderByCreatedAtDesc(Long userId);
}
