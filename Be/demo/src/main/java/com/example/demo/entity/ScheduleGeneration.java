package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_generations" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduleGeneration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_plan_id", nullable = false)
    private RegistrationPlan registrationPlan;

    @Column(name = "generation_no", nullable = false)
    private Integer generationNo;

    @Column(name = "algorithm_version", length = 50)
    @Builder.Default
    private String algorithmVersion = "smartedu-backtrack-2.0";

    @Column(name = "generation_status", nullable = false, length = 30)
    @Builder.Default
    private String generationStatus = "PENDING";

    @Column(name = "total_candidates", nullable = false)
    @Builder.Default
    private Long totalCandidates = 0L;

    @Column(name = "feasible_candidates", nullable = false)
    @Builder.Default
    private Long feasibleCandidates = 0L;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;
}
