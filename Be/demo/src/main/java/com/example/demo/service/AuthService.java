package com.example.demo.service;

import com.example.demo.common.BusinessException;
import com.example.demo.common.CryptoUtils;
import com.example.demo.dto.AuthDtos.*;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import com.example.demo.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final EmailOtpRepository emailOtpRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserSessionRepository userSessionRepository;
    private final ActivityLogRepository activityLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    // BR-AUTH-04: Theo dõi số lần đăng nhập thất bại chống brute-force
    private final Map<String, Integer> failedLoginAttempts = new ConcurrentHashMap<>();
    private final Map<String, LocalDateTime> temporaryLockUntil = new ConcurrentHashMap<>();

    // US02: Đăng ký tài khoản + gửi OTP 6 số
    @Transactional
    public Map<String, Object> register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        // BR-AUTH-02: Kiểm tra duy nhất Email & Mã sinh viên
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException("BR-AUTH-02", "Tài khoản đã đăng ký với email này.");
        }
        if (req.getStudentCode() != null && !req.getStudentCode().isBlank()
                && userRepository.existsByStudentCode(req.getStudentCode().trim())) {
            throw new BusinessException("BR-AUTH-02", "Mã sinh viên đã được đăng ký trong hệ thống.");
        }

        Role studentRole = roleRepository.findByCode("STUDENT")
                .orElseThrow(() -> new BusinessException("ROLE_NOT_FOUND", "Không tìm thấy vai trò STUDENT"));

        String baseUsername = email.substring(0, email.indexOf('@'));
        String username = userRepository.existsByUsername(baseUsername)
                ? baseUsername + "_" + (System.currentTimeMillis() % 10000)
                : baseUsername;

        User user = User.builder()
                .role(studentRole)
                .studentCode(req.getStudentCode() != null && !req.getStudentCode().isBlank() ? req.getStudentCode().trim() : null)
                .username(username)
                .fullName(req.getFullName().trim())
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .accountStatus("PENDING")
                .build();
        user = userRepository.save(user);

        String otpCode = generateAndSendOtp(user);
        return Map.of(
                "userId", user.getId(),
                "email", user.getEmail(),
                "accountStatus", user.getAccountStatus(),
                "devOtpPreview", otpCode // Hỗ trợ test/demo khi chưa cấu hình SMTP thật
        );
    }

    private String generateAndSendOtp(User user) {
        String otp = String.format("%06d", new SecureRandom().nextInt(1_000_000));
        EmailOtp emailOtp = EmailOtp.builder()
                .user(user)
                .email(user.getEmail())
                .otpHash(CryptoUtils.sha256Hex(otp))
                .expiresAt(LocalDateTime.now().plusMinutes(10)) // BR-AUTH-03: Hết hạn sau 10 phút
                .attemptCount(0)
                .build();
        emailOtpRepository.save(emailOtp);

        emailService.sendEmail(user, user.getEmail(), "EMAIL_VERIFICATION",
                "[SmartEdu] Mã xác thực tài khoản OTP",
                "Mã OTP xác thực của bạn là: " + otp + " (Có hiệu lực trong 10 phút).");
        return otp;
    }

    // US03: Xác thực email bằng mã OTP
    @Transactional
    public UserProfileResponse verifyOtp(VerifyOtpRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy tài khoản với email này."));

        EmailOtp otpRecord = emailOtpRepository.findTopByEmailAndUsedAtIsNullOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new BusinessException("BR-AUTH-03", "Không tìm thấy mã OTP hợp lệ. Vui lòng yêu cầu gửi lại."));

        if (otpRecord.getAttemptCount() >= 5) {
            throw new BusinessException("BR-AUTH-03", "Bạn đã nhập sai OTP quá 5 lần. Vui lòng thử lại sau 30 phút.", HttpStatus.TOO_MANY_REQUESTS);
        }

        if (otpRecord.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("BR-AUTH-03", "Mã OTP đã hết hạn (quá 10 phút).");
        }

        String inputHash = CryptoUtils.sha256Hex(req.getOtp().trim());
        if (!otpRecord.getOtpHash().equals(inputHash)) {
            otpRecord.setAttemptCount(otpRecord.getAttemptCount() + 1);
            emailOtpRepository.save(otpRecord);
            throw new BusinessException("INVALID_OTP", "Mã OTP không chính xác (" + otpRecord.getAttemptCount() + "/5 lần).");
        }

        // BR-AUTH-03: Single-use OTP
        otpRecord.setUsedAt(LocalDateTime.now());
        emailOtpRepository.save(otpRecord);

        user.setAccountStatus("ACTIVE");
        user.setEmailVerifiedAt(LocalDateTime.now());
        user = userRepository.save(user);

        return toProfile(user);
    }

    @Transactional
    public Map<String, Object> resendOtp(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Email không tồn tại trong hệ thống."));
        if ("ACTIVE".equals(user.getAccountStatus())) {
            throw new BusinessException("ALREADY_ACTIVE", "Tài khoản này đã được kích hoạt.");
        }
        String otp = generateAndSendOtp(user);
        return Map.of("email", user.getEmail(), "devOtpPreview", otp);
    }

    // US04: Đăng nhập + JWT Access/Refresh Token
    @Transactional
    public AuthResponse login(LoginRequest req, String ipAddress, String userAgent) {
        String key = req.getIdentifier().trim().toLowerCase();

        // BR-AUTH-04: Kiểm tra khóa tạm thời 15 phút
        LocalDateTime lockUntil = temporaryLockUntil.get(key);
        if (lockUntil != null && lockUntil.isAfter(LocalDateTime.now())) {
            throw new BusinessException("BR-AUTH-04", "Đăng nhập sai quá 5 lần. Tài khoản tạm khóa trong 15 phút.", HttpStatus.TOO_MANY_REQUESTS);
        }

        User user = userRepository.findByEmailOrStudentCodeOrUsername(key, req.getIdentifier().trim(), key)
                .orElseThrow(() -> {
                    recordFailedLogin(key);
                    return new BusinessException("INVALID_CREDENTIALS", "Thông tin đăng nhập hoặc mật khẩu không chính xác.", HttpStatus.UNAUTHORIZED);
                });

        if ("PENDING".equals(user.getAccountStatus())) {
            throw new BusinessException("ACCOUNT_PENDING", "Tài khoản chưa xác thực OTP email.", HttpStatus.FORBIDDEN);
        }
        if ("LOCKED".equals(user.getAccountStatus()) || user.getLockedAt() != null) {
            throw new BusinessException("ACCOUNT_LOCKED", "Tài khoản đã bị quản trị viên khóa.", HttpStatus.FORBIDDEN);
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            recordFailedLogin(key);
            throw new BusinessException("INVALID_CREDENTIALS", "Thông tin đăng nhập hoặc mật khẩu không chính xác.", HttpStatus.UNAUTHORIZED);
        }

        failedLoginAttempts.remove(key);
        temporaryLockUntil.remove(key);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getEmail(), user.getRole().getCode());
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId());

        UserSession session = UserSession.builder()
                .user(user)
                .tokenHash(CryptoUtils.sha256Hex(accessToken))
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .lastActivityAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(jwtTokenProvider.getAccessExpirationMs() / 1000))
                .build();
        userSessionRepository.save(session);

        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresInMs(jwtTokenProvider.getAccessExpirationMs())
                .user(toProfile(user))
                .build();
    }

    private void recordFailedLogin(String key) {
        int attempts = failedLoginAttempts.getOrDefault(key, 0) + 1;
        failedLoginAttempts.put(key, attempts);
        if (attempts >= 5) {
            temporaryLockUntil.put(key, LocalDateTime.now().plusMinutes(15));
            failedLoginAttempts.remove(key);
        }
    }

    // US05: Đăng xuất & Blacklist AccessToken
    @Transactional
    public void logout(String token) {
        if (token != null) {
            userSessionRepository.findByTokenHash(CryptoUtils.sha256Hex(token)).ifPresent(session -> {
                session.setRevokedAt(LocalDateTime.now());
                userSessionRepository.save(session);
            });
        }
    }

    // US06: Khôi phục mật khẩu
    @Transactional
    public Map<String, String> forgotPassword(ForgotPasswordRequest req) {
        String email = req.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy email trong hệ thống."));

        String rawToken = UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.builder()
                .user(user)
                .tokenHash(CryptoUtils.sha256Hex(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(15)) // BR-AUTH-06: Hạn 15 phút
                .build();
        passwordResetTokenRepository.save(resetToken);

        emailService.sendEmail(user, email, "PASSWORD_RESET",
                "[SmartEdu] Khôi phục mật khẩu",
                "Đường dẫn đặt lại mật khẩu (hiệu lực 15 phút): /reset-password?token=" + rawToken);

        return Map.of("message", "Đã gửi hướng dẫn khôi phục mật khẩu qua email.", "devResetToken", rawToken);
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        PasswordResetToken prt = passwordResetTokenRepository.findByTokenHashAndUsedAtIsNull(CryptoUtils.sha256Hex(req.getToken()))
                .orElseThrow(() -> new BusinessException("BR-AUTH-06", "Token đặt lại mật khẩu không hợp lệ hoặc đã sử dụng."));

        if (prt.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("BR-AUTH-06", "Link đặt lại mật khẩu đã hết hạn (quá 15 phút).");
        }

        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        prt.setUsedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(prt);
    }

    // US07: Quản lý hồ sơ cá nhân (BR-AUTH-07: Không sửa email & student_code)
    @Transactional
    public UserProfileResponse updateProfile(User currentUser, UpdateProfileRequest req) {
        if (req.getFullName() != null && !req.getFullName().isBlank()) {
            currentUser.setFullName(req.getFullName().trim());
        }
        if (req.getPhone() != null) currentUser.setPhone(req.getPhone().trim());
        if (req.getAddress() != null) currentUser.setAddress(req.getAddress().trim());
        if (req.getGender() != null) currentUser.setGender(req.getGender().trim());
        if (req.getAvatarUrl() != null) currentUser.setAvatarUrl(req.getAvatarUrl().trim());

        User saved = userRepository.save(currentUser);
        return toProfile(saved);
    }

    @Transactional
    public void changePassword(User currentUser, ChangePasswordRequest req) {
        if (!passwordEncoder.matches(req.getOldPassword(), currentUser.getPasswordHash())) {
            throw new BusinessException("INVALID_OLD_PASSWORD", "Mật khẩu hiện tại không chính xác.");
        }
        currentUser.setPasswordHash(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(currentUser);
    }

    public UserProfileResponse toProfile(User u) {
        return UserProfileResponse.builder()
                .id(u.getId())
                .studentCode(u.getStudentCode())
                .username(u.getUsername())
                .fullName(u.getFullName())
                .email(u.getEmail())
                .phone(u.getPhone())
                .address(u.getAddress())
                .gender(u.getGender())
                .avatarUrl(u.getAvatarUrl())
                .role(u.getRole().getCode())
                .accountStatus(u.getAccountStatus())
                .build();
    }
}
