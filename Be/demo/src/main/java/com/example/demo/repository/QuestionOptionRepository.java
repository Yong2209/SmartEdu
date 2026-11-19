package com.example.demo.repository;
import com.example.demo.entity.QuestionOption;
import org.springframework.data.jpa.repository.JpaRepository;
public interface QuestionOptionRepository extends JpaRepository<QuestionOption, Long> {
}
