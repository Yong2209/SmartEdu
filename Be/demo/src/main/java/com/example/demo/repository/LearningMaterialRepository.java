package com.example.demo.repository;
import com.example.demo.entity.LearningMaterial;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface LearningMaterialRepository extends JpaRepository<LearningMaterial, Long> {
    List<LearningMaterial> findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(Long userId);
    Optional<LearningMaterial> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);
}
