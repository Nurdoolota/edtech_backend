package com.lms.auth.repository;

import com.lms.auth.entity.PasswordResetCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findTopByUserIdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);
}
