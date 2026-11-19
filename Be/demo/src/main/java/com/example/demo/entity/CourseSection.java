package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "course_sections" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CourseSection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "section_code", nullable = false, length = 50)
    private String sectionCode;

    @Column(name = "instructor_name", length = 150)
    private String instructorName;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Column(name = "current_enrollment", nullable = false)
    @Builder.Default
    private Integer currentEnrollment = 0;

    @Column(name = "section_status", nullable = false, length = 30)
    @Builder.Default
    private String sectionStatus = "OPEN";

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "courseSection", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<SectionMeeting> meetings = new ArrayList<>();
}
