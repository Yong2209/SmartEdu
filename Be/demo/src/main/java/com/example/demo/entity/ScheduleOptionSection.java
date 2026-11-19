package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_option_sections" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduleOptionSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_option_id", nullable = false)
    private ScheduleOption scheduleOption;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_section_id", nullable = false)
    private CourseSection courseSection;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
