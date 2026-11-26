package com.smartedu.modules.user_management.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.user_management.dto.admin.AdminUserDtos;
import com.smartedu.modules.user_management.entity.Role;
import com.smartedu.modules.user_management.entity.User;
import com.smartedu.modules.user_management.repository.RoleRepository;
import com.smartedu.modules.user_management.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public List<AdminUserDtos.UserResponse> getUsers(String keyword) {

        List<User> users;

        if (keyword == null || keyword.isBlank()) {
            users = userRepository.findAllByOrderByCreatedAtDesc();
        } else {
            users = userRepository
                    .findByEmailContainingIgnoreCaseOrStudentCodeContainingIgnoreCase(
                            keyword,
                            keyword
                    );
        }

        return users.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AdminUserDtos.UserResponse changeRole(
            Long userId,
            AdminUserDtos.ChangeRoleRequest request
    ) {
        User user = getUser(userId);

        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy vai trò.",
                        HttpStatus.NOT_FOUND
                ));

        user.setRole(role);

        return toResponse(userRepository.save(user));
    }

    @Transactional
    public AdminUserDtos.UserResponse changeStatus(
            Long userId,
            AdminUserDtos.AccountStatusRequest request
    ) {
        User user = getUser(userId);

        String status = request.status().toUpperCase();

        if (!List.of("ACTIVE", "PENDING", "LOCKED").contains(status)) {
            throw new BusinessException(
                    "Trạng thái tài khoản không hợp lệ.",
                    HttpStatus.BAD_REQUEST
            );
        }

        user.setAccountStatus(status);

        if ("LOCKED".equals(status)) {
            user.setLockedAt(java.time.LocalDateTime.now());
        } else {
            user.setLockedAt(null);
        }

        return toResponse(userRepository.save(user));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BusinessException(
                        "Không tìm thấy tài khoản.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private AdminUserDtos.UserResponse toResponse(User user) {
        return new AdminUserDtos.UserResponse(
                user.getId(),
                user.getStudentCode(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getPhone(),
                user.getRole() == null ? null : user.getRole().getCode(),
                user.getAccountStatus(),
                user.getCreatedAt() == null
                        ? null
                        : user.getCreatedAt().toString()
        );
    }
}
