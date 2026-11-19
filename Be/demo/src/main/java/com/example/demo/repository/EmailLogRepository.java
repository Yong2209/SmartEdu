package com.example.demo.repository;
import com.example.demo.entity.EmailLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {
    List<EmailLog> findTop100ByOrderByCreatedAtDesc();
}
