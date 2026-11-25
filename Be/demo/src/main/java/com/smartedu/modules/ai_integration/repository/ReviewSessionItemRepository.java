package com.smartedu.modules.ai_integration.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.ai_integration.entity.ReviewSessionItem;

public interface ReviewSessionItemRepository extends JpaRepository<ReviewSessionItem, Long> {
    Optional<ReviewSessionItem> findByIdAndReviewSessionId(Long id, Long reviewSessionId);
}
