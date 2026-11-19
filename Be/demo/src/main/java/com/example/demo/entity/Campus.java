package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "campuses" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Campus {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "campus_code", nullable = false, unique = true, length = 30)
    private String campusCode;

    @Column(name = "campus_name", nullable = false, unique = true, length = 150)
    private String campusName;

    @Column(length = 500)
    private String address;
}
