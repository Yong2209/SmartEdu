package com.smartedu.modules.learning.service;

import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.smartedu.modules.learning.repository.*;
import com.smartedu.modules.learning.entity.*;
import com.smartedu.modules.learning.dto.response.MaterialContentResponse;
import com.smartedu.modules.user_management.repository.UserRepository;

@Service
public class MaterialContentService {

    private final LearningMaterialRepository materialRepository;
    private final MaterialContentRepository contentRepository;
    private final UserRepository userRepository;
    private final DocumentExtractionService extractionService;

    public MaterialContentService(
        LearningMaterialRepository materialRepository,
        MaterialContentRepository contentRepository,
        UserRepository userRepository,
        DocumentExtractionService extractionService
    ) {
        this.materialRepository = materialRepository;
        this.contentRepository = contentRepository;
        this.userRepository = userRepository;
        this.extractionService = extractionService;
    }

    @Transactional(readOnly = true)
    public MaterialContentResponse getContent(
        String username,
        Long materialId
    ) {
        var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED
            ));

        LearningMaterial material = materialRepository
            .findByIdAndUserIdAndDeletedAtIsNull(
                materialId, user.getId()
            )
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy học liệu"
            ));

        MaterialContent content = contentRepository
            .findByLearningMaterialId(materialId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Chưa có nội dung được trích xuất"
            ));

        return new MaterialContentResponse(
            material.getId(),
            material.getTitle(),
            content.getExtractionStatus(),
            content.getExtractedText()
        );
    }

    @Transactional
    public MaterialContentResponse reExtract(
        String username,
        Long materialId
    ) {
        var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED
            ));

        LearningMaterial material = materialRepository
            .findByIdAndUserIdAndDeletedAtIsNull(
                materialId, user.getId()
            )
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Không tìm thấy học liệu"
            ));

        try {
            Path path = Path.of(material.getStoragePath())
                .toAbsolutePath().normalize();

            if (!Files.isRegularFile(path)) {
                throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Không tìm thấy file gốc"
                );
            }

            String text = extractionService.extract(
                Files.newInputStream(path),
                material.getFileExtension()
            );

            if (text == null || text.isBlank()) {
                throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Không tìm thấy nội dung văn bản"
                );
            }

            MaterialContent content = contentRepository
                .findByLearningMaterialId(materialId)
                .orElseGet(MaterialContent::new);

            content.setLearningMaterial(material);
            content.setExtractedText(text);
            content.setExtractionStatus("COMPLETED");
            content.setExtractedAt(
                java.time.LocalDateTime.now()
            );

            contentRepository.save(content);

            material.setProcessingStatus("COMPLETED");
            materialRepository.save(material);

            return getContent(username, materialId);

        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Không thể trích xuất nội dung"
            );
        }
    }
}
