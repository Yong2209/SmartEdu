package com.smartedu.modules.learning.entity;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import com.smartedu.modules.user_management.entity.User;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "question_generation_requests", schema = "dbo")
public class QuestionGenerationRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "learning_material_id",
        nullable = false
    )
    private LearningMaterial learningMaterial;

    @Column(name = "content_scope",
            nullable = false, length = 30)
    private String contentScope;

    @Column(name = "scope_description",
            columnDefinition = "nvarchar(max)")
    private String scopeDescription;

    @Column(name = "question_type",
            nullable = false, length = 30)
    private String questionType;

    @Column(name = "question_count", nullable = false)
    private Integer questionCount;

    @Column(name = "difficulty",
            nullable = false, length = 20)
    private String difficulty;

    @Column(name = "status",
            nullable = false, length = 30)
    private String status = "PENDING";

    @Column(name = "requested_at",
            insertable = false, updatable = false)
    private LocalDateTime requestedAt;
}
