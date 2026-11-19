package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "quiz_test_questions" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class QuizTestQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_test_id", nullable = false)
    private QuizTest quizTest;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "max_score", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal maxScore = BigDecimal.ONE;
}
