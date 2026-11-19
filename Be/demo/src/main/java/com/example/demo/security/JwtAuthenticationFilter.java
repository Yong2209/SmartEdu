package com.example.demo.security;

import com.example.demo.common.CryptoUtils;
import com.example.demo.entity.User;
import com.example.demo.entity.UserSession;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.UserSessionRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            String token = bearer.substring(7);
            if (jwtTokenProvider.validateToken(token)) {
                // BR-AUTH-05: Kiểm tra token có bị thu hồi (Blacklist / revoked_at) trong user_sessions không
                String tokenHash = CryptoUtils.sha256Hex(token);
                Optional<UserSession> sessionOpt = userSessionRepository.findByTokenHash(tokenHash);
                if (sessionOpt.isPresent() && sessionOpt.get().getRevokedAt() != null) {
                    filterChain.doFilter(request, response);
                    return;
                }

                Claims claims = jwtTokenProvider.parseClaims(token);
                Long userId = Long.parseLong(claims.getSubject());
                String role = claims.get("role", String.class);

                Optional<User> userOpt = userRepository.findById(userId);
                if (userOpt.isPresent() && "ACTIVE".equals(userOpt.get().getAccountStatus())) {
                    User user = userOpt.get();
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                            user,
                            token,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    SecurityContextHolder.getContext().setAuthentication(auth);

                    sessionOpt.ifPresent(s -> {
                        s.setLastActivityAt(LocalDateTime.now());
                        userSessionRepository.save(s);
                    });
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
