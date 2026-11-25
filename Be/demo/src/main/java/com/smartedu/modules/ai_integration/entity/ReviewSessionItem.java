package com.smartedu.modules.ai_integration.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "review_session_items", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSessionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "review_session_id", nullable = false)
    private ReviewSession reviewSession;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_attempt_answer_id")
    private QuizAttemptAnswer sourceAttemptAnswer;

    @Column(name = "review_status", nullable = false, length = 30)
    @Builder.Default
    private String reviewStatus = "PENDING";

    @Column(name = "last_is_correct")
    private Boolean lastIsCorrect;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @OneToMany(mappedBy = "reviewSessionItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReviewAttempt> attempts = new ArrayList<>();
}
