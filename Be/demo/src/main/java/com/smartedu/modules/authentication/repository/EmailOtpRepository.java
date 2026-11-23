package com.smartedu.modules.authentication.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.smartedu.modules.authentication.entity.EmailOtp;

public interface EmailOtpRepository extends JpaRepository<EmailOtp, Long> {

    List<EmailOtp> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<EmailOtp> findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);
}
