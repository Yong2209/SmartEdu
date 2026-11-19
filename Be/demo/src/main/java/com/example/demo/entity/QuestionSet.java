package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "question_sets" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestionSet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generation_request_id")
    private QuestionGenerationRequest generationRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_material_id")
    private LearningMaterial sourceMaterial;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "set_status", nullable = false, length = 30)
    @Builder.Default
    private String setStatus = "DRAFT";

    @Column(name = "saved_at")
    private LocalDateTime savedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "questionSet", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private List<Question> questions = new ArrayList<>();
}
