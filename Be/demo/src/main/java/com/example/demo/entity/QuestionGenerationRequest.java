package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "question_generation_requests" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuestionGenerationRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "learning_material_id", nullable = false)
    private LearningMaterial learningMaterial;

    @Column(name = "content_scope", nullable = false, length = 30)
    private String contentScope;

    @Column(name = "scope_description", columnDefinition = "LONGTEXT")
    private String scopeDescription;

    @Column(name = "question_type", nullable = false, length = 30)
    private String questionType;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(nullable = false, length = 20)
    private String difficulty;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "requested_at")
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;
}
