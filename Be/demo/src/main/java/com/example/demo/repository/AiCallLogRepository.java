package com.example.demo.repository;
import com.example.demo.entity.AiCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {
    List<AiCallLog> findTop100ByOrderByCalledAtDesc();
}
