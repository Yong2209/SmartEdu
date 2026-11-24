package com.smartedu.modules.ai_integration.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import com.smartedu.modules.learning.entity.QuestionGenerationRequest;
import com.smartedu.modules.user_management.entity.User;
@Entity
@Table(name = "ai_call_logs" )
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder 
public class AiCallLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_generation_request_id")
    private QuestionGenerationRequest questionGenerationRequest;

    @Column(name = "operation_type", nullable = false, length = 50)
    private String operationType;

    @Column(length = 100)
    private String provider;

    @Column(name = "model_name", length = 150)
    private String modelName;

    @Column(name = "response_time_ms")
    private Long responseTimeMs;

    @Column(name = "prompt_tokens")
    private Long promptTokens;

    @Column(name = "completion_tokens")
    private Long completionTokens;

    @Column(name = "total_tokens")
    private Long totalTokens;

    @Column(name = "call_status", nullable = false, length = 30)
    private String callStatus;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "LONGTEXT")
    private String errorMessage;

    @Column(name = "called_at")
    @Builder.Default
    private LocalDateTime calledAt = LocalDateTime.now();
}
