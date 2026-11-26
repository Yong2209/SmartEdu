package com.smartedu.modules.schedule_management.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schedule_option_evaluations", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleOptionEvaluation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_option_id", nullable = false)
    private ScheduleOption scheduleOption;

    @Column(name = "criterion_code", nullable = false, length = 50)
    private String criterionCode;

    @Column(name = "criterion_name", nullable = false, length = 150)
    private String criterionName;

    @Column(name = "score_value", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal scoreValue = BigDecimal.ZERO;

    @Column(name = "weight_value", nullable = false, precision = 10, scale = 4)
    @Builder.Default
    private BigDecimal weightValue = BigDecimal.ZERO;

    @Column(name = "explanation", columnDefinition = "nvarchar(max)")
    private String explanation;
}
