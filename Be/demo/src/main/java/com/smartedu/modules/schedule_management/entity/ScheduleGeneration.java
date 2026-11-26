package com.smartedu.modules.schedule_management.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.smartedu.modules.course_registration.entity.RegistrationPlan;

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
@Table(name = "schedule_generations", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleGeneration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "registration_plan_id", nullable = false)
    private RegistrationPlan registrationPlan;

    @Column(name = "generation_no", nullable = false)
    private Integer generationNo;

    @Column(name = "algorithm_version", length = 50)
    private String algorithmVersion;

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

    @Column(name = "error_message", columnDefinition = "nvarchar(max)")
    private String errorMessage;

    @OneToMany(
        mappedBy = "scheduleGeneration",
        cascade = CascadeType.ALL,
        orphanRemoval = true
    )
    @Builder.Default
    private List<ScheduleOption> options = new ArrayList<>();
}
