package com.example.demo.repository;
import com.example.demo.entity.MaterialContent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface MaterialContentRepository extends JpaRepository<MaterialContent, Long> {
    Optional<MaterialContent> findByLearningMaterialId(Long learningMaterialId);
}
