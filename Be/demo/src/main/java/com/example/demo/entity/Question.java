package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "questions" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_set_id", nullable = false)
    private QuestionSet questionSet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_material_id")
    private LearningMaterial sourceMaterial;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaces_question_id")
    private Question replacesQuestion;

    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(columnDefinition = "LONGTEXT")
    private String explanation;

    @Column(length = 20)
    private String difficulty;

    @Column(name = "reference_excerpt", columnDefinition = "LONGTEXT")
    private String referenceExcerpt;

    @Column(name = "reference_page")
    private Long referencePage;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "question_status", nullable = false, length = 30)
    @Builder.Default
    private String questionStatus = "ACTIVE";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<QuestionOption> options = new ArrayList<>();
}
