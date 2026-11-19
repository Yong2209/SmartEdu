package com.example.demo.repository;
import com.example.demo.entity.FileProcessingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface FileProcessingLogRepository extends JpaRepository<FileProcessingLog, Long> {
    List<FileProcessingLog> findTop100ByOrderByProcessedAtDesc();
}
