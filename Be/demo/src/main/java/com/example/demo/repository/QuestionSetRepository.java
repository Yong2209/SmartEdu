package com.example.demo.repository;
import com.example.demo.entity.QuestionSet;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface QuestionSetRepository extends JpaRepository<QuestionSet, Long> {
    List<QuestionSet> findByUserIdOrderByCreatedAtDesc(Long userId);
    Optional<QuestionSet> findByIdAndUserId(Long id, Long userId);
}
