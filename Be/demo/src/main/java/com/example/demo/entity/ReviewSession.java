package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "review_sessions" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_quiz_attempt_id")
    private QuizAttempt sourceQuizAttempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_question_set_id")
    private QuestionSet sourceQuestionSet;

    @Column(name = "session_status", nullable = false, length = 30)
    @Builder.Default
    private String sessionStatus = "IN_PROGRESS";

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @OneToMany(mappedBy = "reviewSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReviewSessionItem> items = new ArrayList<>();
}
