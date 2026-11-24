package com.smartedu.modules.ai_integration.repository;
import com.smartedu.modules.ai_integration.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface QuizAttemptAnswerRepository extends JpaRepository<QuizAttemptAnswer, Long> {
    List<QuizAttemptAnswer> findByQuizAttemptId(Long quizAttemptId);
}
