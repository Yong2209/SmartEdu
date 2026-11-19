package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_attempt_answers" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizAttemptAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_attempt_id", nullable = false)
    private QuizAttempt quizAttempt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_test_question_id", nullable = false)
    private QuizTestQuestion quizTestQuestion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuestionOption selectedOption;

    @Column(name = "answer_text", columnDefinition = "LONGTEXT")
    private String answerText;

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "awarded_score", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal awardedScore = BigDecimal.ZERO;

    @Column(name = "question_content_snapshot", columnDefinition = "LONGTEXT")
    private String questionContentSnapshot;

    @Column(name = "correct_answer_snapshot", columnDefinition = "LONGTEXT")
    private String correctAnswerSnapshot;

    @Column(name = "explanation_snapshot", columnDefinition = "LONGTEXT")
    private String explanationSnapshot;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
