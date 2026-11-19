package com.example.demo.repository;
import com.example.demo.entity.QuizTestQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
public interface QuizTestQuestionRepository extends JpaRepository<QuizTestQuestion, Long> {
}
