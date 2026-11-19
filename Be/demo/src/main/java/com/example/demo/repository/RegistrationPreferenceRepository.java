package com.example.demo.repository;
import com.example.demo.entity.RegistrationPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface RegistrationPreferenceRepository extends JpaRepository<RegistrationPreference, Long> {
    Optional<RegistrationPreference> findByRegistrationPlanId(Long registrationPlanId);
}
