package com.smartedu.modules.schedule_management.entity;

import java.time.LocalDateTime;

import com.smartedu.modules.user_management.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "schedule_exports", schema = "dbo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleExport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "schedule_option_id", nullable = false)
    private ScheduleOption scheduleOption;

    @Column(name = "export_format", nullable = false, length = 10)
    private String exportFormat;

    @Column(name = "file_path", nullable = false, length = 1024)
    private String filePath;

    @Column(name = "export_status", nullable = false, length = 30)
    private String exportStatus;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "exported_at")
    private LocalDateTime exportedAt;
}
