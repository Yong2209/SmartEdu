package com.smartedu.modules.ai_integration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.ai_integration.entity.ReviewSession;

public interface ReviewSessionRepository extends JpaRepository<ReviewSession, Long> {
    List<ReviewSession> findByUserIdOrderByStartedAtDesc(Long userId);
}
