package com.smartedu.modules.ai_integration.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.ai_integration.entity.Question;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByQuestionSetIdAndQuestionStatusNotOrderByDisplayOrderAsc(
            Long questionSetId,
            String excludedStatus);

    List<Question> findByReplacesQuestionIdAndQuestionStatus(Long replacesQuestionId, String questionStatus);
}
