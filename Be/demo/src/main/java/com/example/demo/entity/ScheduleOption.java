package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "schedule_options" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduleOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_generation_id", nullable = false)
    private ScheduleGeneration scheduleGeneration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_option_id")
    private ScheduleOption parentOption;

    @Column(name = "option_name", length = 255)
    private String optionName;

    @Column(name = "option_rank")
    private Integer optionRank;

    @Column(name = "total_credits", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal totalCredits = BigDecimal.ZERO;

    @Column(name = "total_score", precision = 10, scale = 4)
    private BigDecimal totalScore;

    @Column(name = "is_feasible", nullable = false)
    @Builder.Default
    private Boolean isFeasible = true;

    @Column(name = "option_type", nullable = false, length = 30)
    @Builder.Default
    private String optionType = "GENERATED";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "scheduleOption", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ScheduleOptionSection> sections = new ArrayList<>();

    @OneToMany(mappedBy = "scheduleOption", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ScheduleOptionEvaluation> evaluations = new ArrayList<>();

    @OneToMany(mappedBy = "scheduleOption", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<ScheduleOptionWarning> warnings = new ArrayList<>();
}
