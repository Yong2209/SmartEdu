package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "review_attempts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewAttempt {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_session_item_id", nullable = false)
    private ReviewSessionItem reviewSessionItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuestionOption selectedOption;

    @Column(name = "answer_text", columnDefinition = "LONGTEXT")
    private String answerText;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "attempted_at", nullable = false)
    @Builder.Default
    private LocalDateTime attemptedAt = LocalDateTime.now();
}
