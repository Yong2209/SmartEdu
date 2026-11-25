package com.smartedu.modules.course_registration.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.smartedu.modules.academic_management.entity.Semester;
import com.smartedu.modules.user_management.entity.User;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "registration_imports", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(name = "import_type", nullable = false, length = 30)
    private String importType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "storage_path", nullable = false, length = 1024)
    private String storagePath;

    @Column(name = "file_format", nullable = false, length = 20)
    private String fileFormat;

    @Column(name = "import_status", nullable = false, length = 30)
    @Builder.Default
    private String importStatus = "PENDING";

    @Column(name = "total_rows", nullable = false)
    @Builder.Default
    private Long totalRows = 0L;

    @Column(name = "valid_rows", nullable = false)
    @Builder.Default
    private Long validRows = 0L;

    @Column(name = "error_rows", nullable = false)
    @Builder.Default
    private Long errorRows = 0L;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @OneToMany(mappedBy = "registrationImport", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rowNumber ASC")
    @Builder.Default
    private List<RegistrationImportError> errors = new ArrayList<>();
}
