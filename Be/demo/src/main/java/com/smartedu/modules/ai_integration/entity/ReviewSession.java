package com.smartedu.modules.ai_integration.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.smartedu.modules.user_management.entity.User;

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
@Table(name = "review_sessions", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
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
