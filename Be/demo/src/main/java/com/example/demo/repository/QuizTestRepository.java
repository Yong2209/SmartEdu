package com.example.demo.repository;
import com.example.demo.entity.QuizTest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface QuizTestRepository extends JpaRepository<QuizTest, Long> {
    List<QuizTest> findByUserIdOrderByCreatedAtDesc(Long userId);
}
