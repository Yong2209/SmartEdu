package com.smartedu.modules.ai_integration.repository;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.ai_integration.entity.AiCallLog;
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {
    List<AiCallLog> findTop100ByOrderByCalledAtDesc();
}
