package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "schedule_exports" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ScheduleExport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "schedule_option_id", nullable = false)
    private ScheduleOption scheduleOption;

    @Column(name = "export_format", nullable = false, length = 10)
    private String exportFormat; // PNG, PDF

    @Column(name = "file_path", length = 1024)
    private String filePath;

    @Column(name = "export_status", nullable = false, length = 30)
    @Builder.Default
    private String exportStatus = "PENDING";

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "exported_at")
    private LocalDateTime exportedAt;
}
