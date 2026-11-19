package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_option_warnings" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduleOptionWarning {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_option_id", nullable = false)
    private ScheduleOption scheduleOption;

    @Column(name = "warning_type", nullable = false, length = 50)
    private String warningType;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String severity = "INFO";

    @Column(name = "warning_message", nullable = false, columnDefinition = "LONGTEXT")
    private String warningMessage;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
