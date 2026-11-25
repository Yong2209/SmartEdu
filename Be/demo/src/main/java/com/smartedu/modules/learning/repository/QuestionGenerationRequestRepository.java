package com.smartedu.modules.learning.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.learning.entity.QuestionGenerationRequest;

public interface QuestionGenerationRequestRepository
    extends JpaRepository<QuestionGenerationRequest, Long> {

    Optional<QuestionGenerationRequest> findByIdAndUserId(Long id, Long userId);
}
