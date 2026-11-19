package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "registration_preferences" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegistrationPreference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_plan_id", nullable = false, unique = true)
    private RegistrationPlan registrationPlan;

    @Column(name = "min_credits", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal minCredits = new BigDecimal("12.0");

    @Column(name = "max_credits", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal maxCredits = new BigDecimal("24.0");

    @Column(name = "preferred_shift", length = 20)
    private String preferredShift; // MORNING, AFTERNOON, ANY

    @Column(name = "avoid_saturday", nullable = false)
    @Builder.Default
    private Boolean avoidSaturday = false;

    @Column(name = "compact_days", nullable = false)
    @Builder.Default
    private Boolean compactDays = false;

    @Column(name = "minimize_gaps", nullable = false)
    @Builder.Default
    private Boolean minimizeGaps = false;

    @Column(name = "preferred_free_days", columnDefinition = "LONGTEXT")
    private String preferredFreeDays; // JSON array e.g. "[1,7]"

    @Column(name = "max_gap_periods")
    private Short maxGapPeriods;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
