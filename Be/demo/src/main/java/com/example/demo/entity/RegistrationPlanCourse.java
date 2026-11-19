package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registration_plan_courses" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegistrationPlanCourse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_plan_id", nullable = false)
    private RegistrationPlan registrationPlan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "selection_source", nullable = false, length = 30)
    @Builder.Default
    private String selectionSource = "MANUAL";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
