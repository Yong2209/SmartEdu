package com.smartedu.common.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.smartedu.common.exception.BusinessException;
import com.smartedu.modules.user_management.entity.User;
import com.smartedu.modules.user_management.repository.UserRepository;

@Component
public class CurrentUserResolver {

    private final UserRepository userRepository;

    public CurrentUserResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Resolves the currently authenticated user from the Spring Security context.
     */
    public User requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new BusinessException("Vui lòng đăng nhập để sử dụng tính năng này.", HttpStatus.UNAUTHORIZED);
        }
        String principalName = auth.getName();
        return userRepository.findByUsername(principalName)
                .or(() -> userRepository.findByEmail(principalName))
                .orElseThrow(() -> new BusinessException("Không tìm thấy thông tin người dùng.", HttpStatus.UNAUTHORIZED));
    }
}
