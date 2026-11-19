package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "semesters" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Semester {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "semester_code", nullable = false, unique = true, length = 30)
    private String semesterCode;

    @Column(name = "semester_name", nullable = false, length = 100)
    private String semesterName;

    @Column(name = "academic_year", nullable = false, length = 20)
    private String academicYear;

    @Column(name = "term_no", nullable = false)
    private Short termNo;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "registration_start_at")
    private LocalDateTime registrationStartAt;

    @Column(name = "registration_end_at")
    private LocalDateTime registrationEndAt;

    @Column(name = "semester_status", nullable = false, length = 30)
    @Builder.Default
    private String semesterStatus = "PLANNING";
}
