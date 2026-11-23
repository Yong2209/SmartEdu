package com.smartedu.modules.authentication.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import com.smartedu.modules.authentication.entity.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {

    @EntityGraph(attributePaths = "user")
    Optional<UserSession> findByTokenHash(String tokenHash);

    List<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId);
}
