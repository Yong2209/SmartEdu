package com.example.demo.repository;
import com.example.demo.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {
    List<QuizAttempt> findByUserIdOrderByStartedAtDesc(Long userId);
}
