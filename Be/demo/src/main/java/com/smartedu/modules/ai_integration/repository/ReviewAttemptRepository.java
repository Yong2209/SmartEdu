package com.smartedu.modules.ai_integration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.ai_integration.entity.ReviewAttempt;

public interface ReviewAttemptRepository extends JpaRepository<ReviewAttempt, Long> {
    List<ReviewAttempt> findByReviewSessionItemIdOrderByAttemptedAtDesc(Long reviewSessionItemId);
}
