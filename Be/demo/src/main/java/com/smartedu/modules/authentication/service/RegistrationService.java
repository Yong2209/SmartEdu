package com.smartedu.modules.authentication.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;

import jakarta.transaction.Transactional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.authentication.client.EmailVerificationClient;
import com.smartedu.modules.authentication.dto.request.RegisterRequest;
import com.smartedu.modules.authentication.dto.request.ResendVerificationRequest;
import com.smartedu.modules.authentication.dto.request.VerifyEmailRequest;
import com.smartedu.modules.authentication.dto.response.RegisterResponse;
import com.smartedu.modules.authentication.entity.EmailOtp;
import com.smartedu.modules.authentication.repository.EmailOtpRepository;
import com.smartedu.modules.user_management.entity.Role;
import com.smartedu.modules.user_management.entity.User;
import com.smartedu.modules.user_management.repository.RoleRepository;
import com.smartedu.modules.user_management.repository.UserRepository;

@Service
public class RegistrationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationClient emailClient;
    private final int otpExpirationMinutes;
    private final int maxOtpAttempts;

    public RegistrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            EmailOtpRepository emailOtpRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationClient emailClient,
            @Value("${smartedu.auth.email-otp-expiration-minutes:15}") int otpExpirationMinutes,
            @Value("${smartedu.auth.max-email-otp-attempts:5}") int maxOtpAttempts) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.emailOtpRepository = emailOtpRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailClient = emailClient;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.maxOtpAttempts = maxOtpAttempts;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmail(email) || userRepository.existsByUsername(email)) {
            throw new BusinessException("Email has already been registered");
        }

        Role studentRole = roleRepository.findByCode("STUDENT")
                .orElseThrow(() -> new BusinessException("STUDENT role is not configured"));

        User user = new User();
        user.setRole(studentRole);
        user.setUsername(email);
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPhone(normalizeOptional(request.phone()));
        user.setGender(normalizeOptional(request.gender()));
        user.setAddress(normalizeOptional(request.address()));
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setAccountStatus("PENDING");

        User savedUser = userRepository.save(user);
        sendVerificationOtp(savedUser);

        return new RegisterResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getAccountStatus(),
                "Registration successful. Please verify the OTP sent to your email.");
    }

    @Transactional
    public void resendVerification(ResendVerificationRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (user.getEmailVerifiedAt() != null) {
            throw new BusinessException("Email has already been verified");
        }

        sendVerificationOtp(user);
    }

    @Transactional
    public void verifyEmail(VerifyEmailRequest request) {
        String email = request.email().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("User not found"));

        if (user.getEmailVerifiedAt() != null) {
            throw new BusinessException("Email has already been verified");
        }

        EmailOtp otp = emailOtpRepository
                .findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new BusinessException("Verification OTP not found"));

        if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("Verification OTP has expired");
        }

        if (otp.getAttemptCount() >= maxOtpAttempts) {
            throw new BusinessException("Maximum OTP attempts exceeded");
        }

        if (!java.security.MessageDigest.isEqual(
                constantTimeHexSha256(request.otp()).getBytes(StandardCharsets.UTF_8),
                otp.getOtpHash().getBytes(StandardCharsets.UTF_8))) {
            otp.setAttemptCount(otp.getAttemptCount() + 1);
            emailOtpRepository.save(otp);
            throw new BusinessException("Invalid verification OTP");
        }

        otp.setUsedAt(LocalDateTime.now());
        emailOtpRepository.save(otp);

        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setAccountStatus("ACTIVE");
        userRepository.save(user);
    }

    private void sendVerificationOtp(User user) {
        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        String otpHash = constantTimeHexSha256(otp);

        EmailOtp emailOtp = new EmailOtp();
        emailOtp.setUser(user);
        emailOtp.setEmail(user.getEmail());
        emailOtp.setOtpHash(otpHash);
        emailOtp.setExpiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes));
        emailOtp.setAttemptCount(0);
        emailOtpRepository.save(emailOtp);

        emailClient.sendOtp(user.getEmail(), otp);
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String constantTimeHexSha256(String value) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte part : hash) {
                result.append(String.format("%02x", part & 0xff));
            }
            return result.toString();
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
