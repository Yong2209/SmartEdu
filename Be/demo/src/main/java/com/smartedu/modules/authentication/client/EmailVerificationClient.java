package com.smartedu.modules.authentication.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailVerificationClient {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailVerificationClient(
            JavaMailSender mailSender,
            @Value("${smartedu.mail.from:}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendOtp(String recipient, String otp) {
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
}
