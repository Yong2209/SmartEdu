package com.smartedu.modules.learning.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.learning.entity.QuestionGenerationRequest;

public interface QuestionGenerationRequestRepository
    extends JpaRepository<QuestionGenerationRequest, Long> {
}
