package com.smartedu.modules.authentication.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.smartedu.modules.authentication.client.EmailVerificationClient;
import com.smartedu.modules.authentication.dto.request.ForgotPasswordRequest;
import com.smartedu.modules.authentication.dto.request.ResetPasswordRequest;
import com.smartedu.modules.authentication.entity.PasswordResetToken;
import com.smartedu.modules.authentication.repository.PasswordResetTokenRepository;
import com.smartedu.modules.authentication.repository.UserSessionRepository;
import com.smartedu.modules.user_management.entity.User;
import com.smartedu.modules.user_management.repository.UserRepository;

@Service
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationClient emailClient;
    private final int expirationMinutes;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            UserSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationClient emailClient,
            @Value("${smartedu.auth.password-reset-expiration-minutes:30}") int expirationMinutes) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailClient = emailClient;
        this.expirationMinutes = expirationMinutes;
    }

    @Transactional
    public void requestReset(ForgotPasswordRequest request) {
        userRepository.findByEmail(request.email().trim().toLowerCase())
                .ifPresent(this::createAndSendToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hashToken(request.token()))
                .filter(token -> token.getUsedAt() == null)
                .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new com.smartedu.common.exception.BusinessException(
                        "Reset token is invalid or expired"));

        User user = resetToken.getUser();
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        userRepository.save(user);
        resetToken.setUsedAt(LocalDateTime.now());
        tokenRepository.save(resetToken);

        sessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).forEach(session -> {
            session.setRevokedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    private void createAndSendToken(User user) {
        String token = generateToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUser(user);
        resetToken.setTokenHash(hashToken(token));
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(expirationMinutes));
        tokenRepository.save(resetToken);
        emailClient.sendPasswordResetToken(user.getEmail(), token);
    }

    private String generateToken() {
        byte[] token = new byte[32];
        SECURE_RANDOM.nextBytes(token);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
