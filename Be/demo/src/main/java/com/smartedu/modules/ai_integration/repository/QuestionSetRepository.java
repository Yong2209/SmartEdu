package com.smartedu.modules.ai_integration.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.ai_integration.entity.QuestionSet;

public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {
    List<QuestionSet> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<QuestionSet> findByUserIdAndSetStatusInOrderByCreatedAtDesc(Long userId, List<String> statuses);
    Optional<QuestionSet> findByIdAndUserId(Long id, Long userId);
    Optional<QuestionSet> findByGenerationRequestId(Long generationRequestId);
}
