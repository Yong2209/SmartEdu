package com.smartedu.modules.user_management.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.user_management.entity.User;
import com.smartedu.modules.user_management.repository.UserRepository;
import com.smartedu.modules.user_management.dto.request.UpdateProfileRequest;
import com.smartedu.modules.user_management.dto.request.ChangePasswordRequest;
import com.smartedu.modules.user_management.dto.response.ProfileResponse;

@Service
public class ProfileService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public ProfileService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
            .orElseThrow(() ->
                new BusinessException("Không tìm thấy tài khoản"));
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(String username) {
        User user = getUser(username);
        return toResponse(user);
    }

    @Transactional
    public ProfileResponse updateProfile(
        String username,
        UpdateProfileRequest request
    ) {
        User user = getUser(username);

        user.setFullName(request.fullName().trim());
        user.setPhone(normalize(request.phone()));
        user.setAddress(normalize(request.address()));
        user.setGender(normalize(request.gender()));

        User saved = userRepository.save(user);

        return toResponse(saved);
    }

    @Transactional
    public void changePassword(
        String username,
        ChangePasswordRequest request
    ) {
        User user = getUser(username);

        if (!passwordEncoder.matches(
            request.currentPassword(),
            user.getPasswordHash()
        )) {
            throw new BusinessException(
                "Mật khẩu hiện tại không chính xác"
            );
        }

        if (!request.newPassword().equals(
            request.confirmPassword()
        )) {
            throw new BusinessException(
                "Mật khẩu xác nhận không khớp"
            );
        }

        if (passwordEncoder.matches(
            request.newPassword(),
            user.getPasswordHash()
        )) {
            throw new BusinessException(
                "Mật khẩu mới phải khác mật khẩu hiện tại"
            );
        }

        user.setPasswordHash(
            passwordEncoder.encode(request.newPassword())
        );

        userRepository.save(user);
    }

    private String normalize(String value) {
        return value == null || value.isBlank()
            ? null : value.trim();
    }

    private ProfileResponse toResponse(User user) {
        return new ProfileResponse(
            user.getId(),
            user.getStudentCode(),
            user.getFullName(),
            user.getUsername(),
            user.getEmail(),
            user.getPhone(),
            user.getAddress(),
            user.getGender(),
            user.getAvatarUrl()
        );
    }
}
