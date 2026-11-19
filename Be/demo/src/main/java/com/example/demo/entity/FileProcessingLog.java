package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "file_processing_logs" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FileProcessingLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_material_id")
    private LearningMaterial learningMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_import_id")
    private RegistrationImport registrationImport;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_catalog_import_id")
    private CourseCatalogImport courseCatalogImport;

    @Column(name = "processing_type", nullable = false, length = 50)
    private String processingType;

    @Column(name = "processing_status", nullable = false, length = 30)
    private String processingStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(name = "processed_at")
    @Builder.Default
    private LocalDateTime processedAt = LocalDateTime.now();
}
