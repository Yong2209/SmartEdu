package com.smartedu.modules.learning.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "material_contents", schema = "dbo")
public class MaterialContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "learning_material_id",
        nullable = false,
        unique = true
    )
    private LearningMaterial learningMaterial;

    @Column(name = "extracted_text",
            columnDefinition = "nvarchar(max)")
    private String extractedText;

    @Column(name = "page_count")
    private Long pageCount;

    @Column(name = "extraction_status",
            nullable = false, length = 30)
    private String extractionStatus = "PENDING";

    @Column(name = "extraction_version", length = 50)
    private String extractionVersion;

    @Column(name = "extracted_at")
    private LocalDateTime extractedAt;
}
