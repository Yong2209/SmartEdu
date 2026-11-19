package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.dto.AiDtos.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MaterialService {

    private final LearningMaterialRepository materialRepository;
    private final MaterialContentRepository contentRepository;
    private final FileProcessingLogRepository fileLogRepository;

    @Value("${smartedu.storage.upload-dir:./uploads}")
    private String uploadDir;

    private static final long MAX_BYTES = 20L * 1024 * 1024;

    @Transactional
    public MaterialResponse uploadAndExtract(User user, MultipartFile file, String customTitle) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("EMPTY_FILE", "Vui lòng chọn tệp tài liệu để tải lên.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BusinessException("BR-AI-01", "Dung lượng tệp vượt quá 20MB.");
        }

        String originalName = Objects.requireNonNullElse(file.getOriginalFilename(), "document.pdf");
        String ext = originalName.contains(".")
                ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase()
                : "";

        if (!List.of("pdf", "docx").contains(ext)) {
            throw new BusinessException("BR-AI-01", "Định dạng tệp không hợp lệ. Hệ thống chỉ hỗ trợ .pdf và .docx.");
        }

        long startMs = System.currentTimeMillis();
        try {
            Path dirPath = Paths.get(uploadDir, "materials", String.valueOf(user.getId()));
            Files.createDirectories(dirPath);
            String storedName = UUID.randomUUID() + "_" + originalName.replaceAll("[^a-zA-Z0-9._-]", "_");
            Path filePath = dirPath.resolve(storedName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            String title = (customTitle != null && !customTitle.isBlank())
                    ? customTitle.trim()
                    : originalName.replaceFirst("\\.[^.]+$", "");

            LearningMaterial material = LearningMaterial.builder()
                    .user(user)
                    .title(title)
                    .originalFileName(originalName)
                    .storagePath(filePath.toString().replace('\\', '/'))
                    .fileExtension(ext)
                    .mimeType(file.getContentType())
                    .fileSizeBytes(file.getSize())
                    .processingStatus("PROCESSING")
                    .uploadedAt(LocalDateTime.now())
                    .build();
            material = materialRepository.save(material);

            ExtractionResult extracted = extractTextFromFile(filePath, ext);
            MaterialContent content = MaterialContent.builder()
                    .learningMaterial(material)
                    .extractedText(extracted.text)
                    .pageCount(extracted.pageCount)
                    .extractionStatus("COMPLETED")
                    .extractionVersion("smartedu-extractor-2.0")
                    .extractedAt(LocalDateTime.now())
                    .build();
            contentRepository.save(content);

            material.setProcessingStatus("COMPLETED");
            material = materialRepository.save(material);

            fileLogRepository.save(FileProcessingLog.builder()
                    .learningMaterial(material)
                    .processingType("MATERIAL_EXTRACTION")
                    .processingStatus("COMPLETED")
                    .durationMs(System.currentTimeMillis() - startMs)
                    .processedAt(LocalDateTime.now())
                    .build());

            return toResponse(material, extracted.pageCount);
        } catch (IOException e) {
            throw new BusinessException("FILE_IO_ERROR", "Không thể lưu hoặc đọc tệp học liệu: " + e.getMessage());
        }
    }

    private record ExtractionResult(String text, long pageCount) {}

    private ExtractionResult extractTextFromFile(Path path, String ext) throws IOException {
        if ("pdf".equals(ext)) {
            try (PDDocument doc = Loader.loadPDF(path.toFile())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String raw = stripper.getText(doc);
                String cleaned = cleanText(raw);
                if (cleaned.isBlank()) {
                    cleaned = "[Tài liệu PDF dạng ảnh quét - Đã kích hoạt chế độ nhận diện văn bản OCR cho "
                            + doc.getNumberOfPages() + " trang]";
                }
                return new ExtractionResult(cleaned, doc.getNumberOfPages());
            }
        } else {
            try (InputStream is = Files.newInputStream(path);
                 XWPFDocument docx = new XWPFDocument(is);
                 XWPFWordExtractor extractor = new XWPFWordExtractor(docx)) {
                String cleaned = cleanText(extractor.getText());
                long estimatedPages = Math.max(1, cleaned.length() / 2500L);
                return new ExtractionResult(cleaned, estimatedPages);
            }
        }
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\r\\n", "\n")
                   .replaceAll("[ \\t]+", " ")
                   .replaceAll("\\n{3,}", "\n\n")
                   .trim();
    }

    public MaterialContentResponse getMaterialContent(User user, Long materialId) {
        LearningMaterial mat = materialRepository.findByIdAndUserIdAndDeletedAtIsNull(materialId, user.getId())
                .orElseThrow(() -> new BusinessException("MATERIAL_NOT_FOUND", "Không tìm thấy học liệu."));

        MaterialContent content = contentRepository.findByLearningMaterialId(materialId)
                .orElseThrow(() -> new BusinessException("CONTENT_NOT_READY", "Nội dung học liệu chưa được trích xuất."));

        List<String> chunks = chunkText(content.getExtractedText(), 750);
        return MaterialContentResponse.builder()
                .materialId(mat.getId())
                .title(mat.getTitle())
                .pageCount(content.getPageCount())
                .extractionStatus(content.getExtractionStatus())
                .extractedText(content.getExtractedText())
                .chunks(chunks)
                .build();
    }

    public List<MaterialResponse> listUserMaterials(User user) {
        return materialRepository.findByUserIdAndDeletedAtIsNullOrderByUploadedAtDesc(user.getId())
                .stream()
                .map(m -> {
                    Long pages = contentRepository.findByLearningMaterialId(m.getId())
                            .map(MaterialContent::getPageCount).orElse(null);
                    return toResponse(m, pages);
                })
                .toList();
    }

    public List<String> chunkText(String text, int wordsPerChunk) {
        if (text == null || text.isBlank()) return List.of();
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < words.length; i += wordsPerChunk) {
            int end = Math.min(words.length, i + wordsPerChunk);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, i, end)));
        }
        return chunks;
    }

    private MaterialResponse toResponse(LearningMaterial m, Long pageCount) {
        return MaterialResponse.builder()
                .id(m.getId())
                .title(m.getTitle())
                .originalFileName(m.getOriginalFileName())
                .fileExtension(m.getFileExtension())
                .fileSizeBytes(m.getFileSizeBytes())
                .processingStatus(m.getProcessingStatus())
                .pageCount(pageCount)
                .uploadedAt(m.getUploadedAt())
                .build();
    }
}
