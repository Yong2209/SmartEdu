package com.smartedu.modules.learning.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.learning.entity.LearningMaterial;

public interface LearningMaterialRepository
        extends JpaRepository<LearningMaterial, Long> {

    // Lấy danh sách học liệu chưa bị xóa của một người dùng
    List<LearningMaterial>
    findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(
        Long userId
    );

    // Tìm học liệu theo ID và người sở hữu,
    // đồng thời loại bỏ học liệu đã bị xóa
    Optional<LearningMaterial>
    findByIdAndUserIdAndDeletedAtIsNull(
        Long id,
        Long userId
    );


    List<LearningMaterial>
    findByUserIdAndProcessingStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
        Long userId,
        String processingStatus
    );

}
