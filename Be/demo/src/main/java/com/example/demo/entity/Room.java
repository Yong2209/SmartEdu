package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "rooms" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "campus_id", nullable = false)
    private Campus campus;

    @Column(name = "room_code", nullable = false, length = 30)
    private String roomCode;

    @Column(name = "room_name", length = 100)
    private String roomName;

    private Integer capacity;
}
