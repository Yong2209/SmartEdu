package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "section_meetings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SectionMeeting {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_section_id", nullable = false)
    private CourseSection courseSection;

    @Column(nullable = false)
    private Short weekday; // 2=Thứ 2 ... 7=Thứ 7, 1=CN

    @Column(name = "start_period", nullable = false)
    private Short startPeriod;

    @Column(name = "end_period", nullable = false)
    private Short endPeriod;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id")
    private Room room;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}