package com.example.demo.security;

import com.example.demo.common.BusinessException;
import com.example.demo.entity.User;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthContextHelper {
    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof User user)) {
            throw new BusinessException("UNAUTHORIZED", "Phiên đăng nhập không hợp lệ hoặc đã hết hạn.", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    public String getCurrentToken() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getCredentials() instanceof String token) {
            return token;
        }
        return null;
    }
}
