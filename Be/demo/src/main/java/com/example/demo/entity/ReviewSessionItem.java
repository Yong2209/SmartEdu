package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_session_items" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewSessionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_session_id", nullable = false)
    private ReviewSession reviewSession;

    @ManyToOne(fetch = FetchType.EAGER)
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
}
