package com.smartedu.modules.course_registration.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "registration_preferences", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_plan_id", nullable = false, unique = true)
    private RegistrationPlan registrationPlan;

    @Column(name = "min_credits", nullable = false, precision = 5, scale = 1)
    private BigDecimal minCredits;

    @Column(name = "max_credits", nullable = false, precision = 5, scale = 1)
    private BigDecimal maxCredits;

    @Column(name = "preferred_shift", length = 20)
    private String preferredShift;

    @Column(name = "avoid_saturday", nullable = false)
    @Builder.Default
    private Boolean avoidSaturday = false;

    @Column(name = "compact_days", nullable = false)
    @Builder.Default
    private Boolean compactDays = false;

    @Column(name = "minimize_gaps", nullable = false)
    @Builder.Default
    private Boolean minimizeGaps = false;

    @Column(name = "preferred_free_days", columnDefinition = "nvarchar(max)")
    private String preferredFreeDays;

    @Column(name = "max_gap_periods")
    private Integer maxGapPeriods;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
