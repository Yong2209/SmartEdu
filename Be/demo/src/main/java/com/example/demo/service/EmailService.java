package com.example.demo.service;

import com.example.demo.entity.EmailLog;
import com.example.demo.entity.User;
import com.example.demo.repository.EmailLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final EmailLogRepository emailLogRepository;

    @Value("${smartedu.mail.mock-enabled:true}")
    private boolean mockEnabled;

    public void sendEmail(User user, String toEmail, String emailType, String subject, String body) {
        EmailLog.EmailLogBuilder logBuilder = EmailLog.builder()
                .user(user)
                .recipientEmail(toEmail)
                .emailType(emailType)
                .provider(mockEnabled ? "SMARTEDU_MOCK_SMTP" : "GMAIL_SMTP");

        try {
            if (mockEnabled) {
                log.info("[MOCK EMAIL] To: {} | Type: {} | Subject: {} | Body: {}", toEmail, emailType, subject, body);
                logBuilder.providerMessageId("mock-" + UUID.randomUUID())
                          .sendStatus("SENT")
                          .sentAt(LocalDateTime.now());
            } else {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setTo(toEmail);
                msg.setSubject(subject);
                msg.setText(body);
                mailSender.send(msg);
                logBuilder.providerMessageId("smtp-" + UUID.randomUUID())
                          .sendStatus("SENT")
                          .sentAt(LocalDateTime.now());
            }
        } catch (Exception e) {
            log.error("Gửi email thất bại tới {}: {}", toEmail, e.getMessage());
            logBuilder.sendStatus("FAILED").errorMessage(e.getMessage());
        } finally {
            emailLogRepository.save(logBuilder.build());
        }
    }
}
