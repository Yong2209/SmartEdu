package com.smartedu.modules.ai_integration.repository;
import com.smartedu.modules.ai_integration.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {
    List<QuestionSet> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<QuestionSet> findByIdAndUserId(Long id, Long userId);
}
