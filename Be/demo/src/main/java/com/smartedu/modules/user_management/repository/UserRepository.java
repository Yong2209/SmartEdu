package com.smartedu.modules.user_management.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.user_management.entity.User;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByStudentCode(String studentCode);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    boolean existsByStudentCode(String studentCode);
}
