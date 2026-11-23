package com.smartedu.modules.ai_integration.repository;
import com.smartedu.modules.ai_integration.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUserIdOrderByStartedAtDesc(Long userId);
}
