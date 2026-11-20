package com.smartedu.modules.authentication.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class EmailVerificationClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailVerificationClient.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;
    private final boolean consoleFallback;

    public EmailVerificationClient(
            JavaMailSender mailSender,
            @Value("${smartedu.mail.from:}") String fromAddress,
            @Value("${smartedu.mail.console-fallback:false}") boolean consoleFallback) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
        this.consoleFallback = consoleFallback;
    }

    public void sendOtp(String recipient, String otp) {
        if (consoleFallback && fromAddress.isBlank()) {
            LOGGER.warn("Development OTP for {}: {}", recipient, otp);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (!fromAddress.isBlank()) {
            message.setFrom(fromAddress);
        }
        message.setTo(recipient);
        message.setSubject("Ma xac thuc tai khoan SmartEdu");
        message.setText("Ma OTP cua ban la: " + otp
                + "\nMa co hieu luc trong 15 phut va chi su dung mot lan.");
        mailSender.send(message);
    }

    public void sendPasswordResetToken(String recipient, String token) {
        if (consoleFallback && fromAddress.isBlank()) {
            LOGGER.warn("Development password reset token for {}: {}", recipient, token);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        if (!fromAddress.isBlank()) {
            message.setFrom(fromAddress);
        }
        message.setTo(recipient);
        message.setSubject("Dat lai mat khau SmartEdu");
        message.setText("Ma dat lai mat khau cua ban la: " + token
                + "\nMa co hieu luc trong 30 phut va chi su dung mot lan.");
        mailSender.send(message);
    }
}
