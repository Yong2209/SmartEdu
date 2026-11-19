package com.example.demo.repository;
import com.example.demo.entity.ReviewSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ReviewSessionRepository extends JpaRepository<ReviewSession, Long> {
    List<ReviewSession> findByUserIdOrderByStartedAtDesc(Long userId);
}
