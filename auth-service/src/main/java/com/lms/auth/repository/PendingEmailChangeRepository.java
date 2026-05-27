package com.lms.auth.repository;

import com.lms.auth.entity.PendingEmailChange;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingEmailChangeRepository extends JpaRepository<PendingEmailChange, Long> {

    Optional<PendingEmailChange> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    void deleteAllByUserId(Long userId);
}
