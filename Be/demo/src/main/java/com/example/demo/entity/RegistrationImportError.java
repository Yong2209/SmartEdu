package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "registration_import_errors" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class RegistrationImportError {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_import_id", nullable = false)
    private RegistrationImport registrationImport;

    @Column(name = "[row_number]", nullable = false)
    private Long rowNumber;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    @Column(name = "error_message", nullable = false, columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(name = "row_data", columnDefinition = "LONGTEXT")
    private String rowData;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}
