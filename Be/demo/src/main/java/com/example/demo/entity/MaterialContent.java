package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "material_contents" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MaterialContent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_material_id", nullable = false, unique = true)
    private LearningMaterial learningMaterial;

    @Column(name = "extracted_text", columnDefinition = "LONGTEXT")
    private String extractedText;

    @Column(name = "page_count")
    private Long pageCount;

    @Column(name = "extraction_status", nullable = false, length = 30)
    @Builder.Default
    private String extractionStatus = "PENDING";

    @Column(name = "extraction_version", length = 50)
    private String extractionVersion;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;
}
