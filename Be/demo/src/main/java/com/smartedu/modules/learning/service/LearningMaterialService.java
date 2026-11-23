package com.smartedu.modules.learning.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.smartedu.modules.learning.entity.*;
import com.smartedu.modules.learning.repository.*;
import com.smartedu.modules.learning.dto.response.LearningMaterialResponse;
import com.smartedu.modules.user_management.repository.UserRepository;

@Service
public class LearningMaterialService {

    private final LearningMaterialRepository materialRepository;
    private final MaterialContentRepository contentRepository;
    private final UserRepository userRepository;
    private final DocumentExtractionService extractionService;
    private final Path uploadDir;

    private static final long MAX_SIZE = 10 * 1024 * 1024;

    public LearningMaterialService(
        LearningMaterialRepository materialRepository,
        MaterialContentRepository contentRepository,
        UserRepository userRepository,
        DocumentExtractionService extractionService,
        @Value("${smartedu.storage.upload-dir:uploads}")
        String uploadDir
    ) {
        this.materialRepository = materialRepository;
        this.contentRepository = contentRepository;
        this.userRepository = userRepository;
        this.extractionService = extractionService;
        this.uploadDir = Paths.get(uploadDir)
            .toAbsolutePath().normalize();
    }

    @Transactional
    public LearningMaterialResponse upload(
        String username,
        String title,
        MultipartFile file
    ) {
        try {
            // 1. Kiểm tra tài khoản
            var user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Không tìm thấy tài khoản"
                ));

            // 2. Kiểm tra tên tài liệu
            if (title == null || title.isBlank()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tên tài liệu không được để trống"
                );
            }

            // 3. Kiểm tra file
            if (file == null || file.isEmpty()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vui lòng chọn file"
                );
            }

            if (file.getSize() > MAX_SIZE) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File vượt quá 10MB"
                );
            }

            String originalName = Paths.get(
                file.getOriginalFilename() == null
                    ? "document" : file.getOriginalFilename()
            ).getFileName().toString();

            String extension = getExtension(originalName);

            if (!extension.equals("pdf")
                && !extension.equals("docx")) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Chỉ chấp nhận PDF hoặc DOCX"
                );
            }

            // 4. Đọc thử để kiểm tra file có hợp lệ không
            byte[] bytes = file.getBytes();

            String extractedText = extractionService.extract(
                new ByteArrayInputStream(bytes),
                extension
            );

            if (extractedText == null || extractedText.isBlank()) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Không tìm thấy nội dung văn bản trong file"
                );
            }

            // 5. Tạo tên file ngẫu nhiên
            Files.createDirectories(uploadDir);

            String storedName = UUID.randomUUID()
                + "." + extension;

            Path destination = uploadDir.resolve(storedName)
                .normalize();

            if (!destination.startsWith(uploadDir)) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Đường dẫn file không hợp lệ"
                );
            }

            Files.write(destination, bytes);

            // 6. Lưu thông tin học liệu
            LearningMaterial material = new LearningMaterial();

            material.setUser(user);
            material.setTitle(title.trim());
            material.setOriginalFileName(originalName);
            material.setStoragePath(destination.toString());
            material.setFileExtension(extension);
            material.setMimeType(file.getContentType());
            material.setFileSizeBytes(file.getSize());
            material.setProcessingStatus("COMPLETED");

            material = materialRepository.save(material);

            // 7. Lưu nội dung trích xuất
            MaterialContent content = new MaterialContent();

            content.setLearningMaterial(material);
            content.setExtractedText(extractedText);
            content.setExtractionStatus("COMPLETED");
            content.setExtractionVersion("1.0");
            content.setExtractedAt(
                java.time.LocalDateTime.now()
            );

            contentRepository.save(content);

            // 8. Trả kết quả
            return toResponse(material);

        } catch (IOException exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Không thể đọc hoặc lưu file"
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "File bị lỗi hoặc không thể trích xuất"
            );
        }
    }

    @Transactional(readOnly = true)
    public List<LearningMaterialResponse> getMyMaterials(
        String username
    ) {
        var user = userRepository.findByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.UNAUTHORIZED
            ));

        return materialRepository
            .findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(
                user.getId()
            )
            .stream()
            .map(this::toResponse)
            .toList();
    }

    private String getExtension(String filename) {
        int index = filename.lastIndexOf(".");

        if (index < 0) {
            return "";
        }

        return filename.substring(index + 1)
            .toLowerCase();
    }

    private LearningMaterialResponse toResponse(
        LearningMaterial material
    ) {
        return new LearningMaterialResponse(
            material.getId(),
            material.getTitle(),
            material.getOriginalFileName(),
            material.getFileExtension(),
            material.getFileSizeBytes(),
            material.getProcessingStatus(),
            material.getUploadedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<LearningMaterialResponse> getReadyMaterials(
        String username
    ) {
        var user = userRepository.findByUsername(username)
            .orElseThrow();

        return materialRepository
            .findByUserIdAndProcessingStatusAndDeletedAtIsNullOrderByUploadedAtDesc(
                user.getId(), "COMPLETED"
            )
            .stream()
            .filter(material ->
                contentRepository
                    .findByLearningMaterialId(material.getId())
                    .map(content ->
                        "COMPLETED".equals(content.getExtractionStatus())
                        && content.getExtractedText() != null
                        && !content.getExtractedText().isBlank()
                    )
                    .orElse(false)
            )
            .map(this::toResponse)
            .toList();
    }
}
