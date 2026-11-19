package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "course_catalog_imports")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CourseCatalogImport {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Column(name = "import_status", nullable = false, length = 30)
    @Builder.Default
    private String importStatus = "PENDING";

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Long totalRows = 0L;

    @Column(name = "success_rows", nullable = false)
    @Builder.Default
    private Long successRows = 0L;

    @Column(name = "error_rows", nullable = false)
    @Builder.Default
    private Long errorRows = 0L;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
