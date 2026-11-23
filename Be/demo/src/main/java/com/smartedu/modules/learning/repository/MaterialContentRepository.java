package com.smartedu.modules.learning.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.learning.entity.MaterialContent;

public interface MaterialContentRepository
    extends JpaRepository<MaterialContent, Long> {

    Optional<MaterialContent> findByLearningMaterialId(
        Long learningMaterialId
    );
}
