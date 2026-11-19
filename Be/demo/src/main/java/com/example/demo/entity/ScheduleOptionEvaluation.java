package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "schedule_option_evaluations" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduleOptionEvaluation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_option_id", nullable = false)
    private ScheduleOption scheduleOption;

    @Column(name = "criterion_code", nullable = false, length = 50)
    private String criterionCode;

    @Column(name = "criterion_name", nullable = false, length = 150)
    private String criterionName;

    @Column(name = "score_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal scoreValue;

    @Column(name = "weight_value", nullable = false, precision = 10, scale = 4)
    private BigDecimal weightValue;

    @Column(columnDefinition = "LONGTEXT")
    private String explanation;
}
