package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "saved_schedules" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SavedSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "schedule_option_id", nullable = false)
    private ScheduleOption scheduleOption;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(name = "saved_at")
    @Builder.Default
    private LocalDateTime savedAt = LocalDateTime.now();

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
