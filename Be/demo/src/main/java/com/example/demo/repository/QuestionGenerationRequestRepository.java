package com.example.demo.repository;
import com.example.demo.entity.QuestionGenerationRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface QuestionGenerationRequestRepository extends JpaRepository<QuestionGenerationRequest, Long> {
    List<QuestionGenerationRequest> findByUserIdOrderByRequestedAtDesc(Long userId);
}
