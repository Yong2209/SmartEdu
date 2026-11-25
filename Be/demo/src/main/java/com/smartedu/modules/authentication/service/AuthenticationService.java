package com.smartedu.modules.authentication.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.authentication.dto.request.LoginRequest;
import com.smartedu.modules.authentication.dto.response.LoginResponse;
import com.smartedu.modules.authentication.entity.UserSession;
import com.smartedu.modules.authentication.repository.UserSessionRepository;
import com.smartedu.modules.user_management.entity.User;
import com.smartedu.modules.user_management.repository.UserRepository;

@Service
public class AuthenticationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final long expirationMs;

    public AuthenticationService(
            UserRepository userRepository,
            UserSessionRepository userSessionRepository,
            PasswordEncoder passwordEncoder,
            @Value("${smartedu.security.jwt.expiration-ms:3600000}") long expirationMs) {
        this.userRepository = userRepository;
        this.userSessionRepository = userSessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.expirationMs = expirationMs;
    }

    @Transactional
    public LoginResponse login(LoginRequest request) {
        String username = request.username().trim();
        User user = userRepository.findByEmail(username.toLowerCase())
                .or(() -> userRepository.findByUsername(username))
                .or(() -> userRepository.findByStudentCode(username))
                .orElseThrow(() -> new BusinessException(
                        "Invalid username or password", HttpStatus.UNAUTHORIZED));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException("Invalid username or password", HttpStatus.UNAUTHORIZED);
        }
        if (!"ACTIVE".equalsIgnoreCase(user.getAccountStatus())
                || user.getEmailVerifiedAt() == null) {
            throw new BusinessException(
                    "Please verify your email before signing in", HttpStatus.FORBIDDEN);
        }
        if (user.getLockedAt() != null || "LOCKED".equalsIgnoreCase(user.getAccountStatus())) {
            throw new BusinessException("This account is locked", HttpStatus.FORBIDDEN);
        }

        String accessToken = generateToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusNanos(expirationMs * 1_000_000);

        UserSession session = new UserSession();
        session.setUser(user);
        session.setTokenHash(hashToken(accessToken));
        session.setLastActivityAt(LocalDateTime.now());
        session.setExpiresAt(expiresAt);
        userSessionRepository.save(session);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return new LoginResponse(
                accessToken,
                expiresAt.toInstant(ZoneOffset.UTC),
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().getCode());
    }

    @Transactional
    public void logout(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        userSessionRepository.findByTokenHash(hashToken(accessToken)).ifPresent(session -> {
            session.setRevokedAt(LocalDateTime.now());
            userSessionRepository.save(session);
        });
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
