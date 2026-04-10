package com.lms.auth.repository;

import com.lms.auth.entity.ImpersonationAudit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImpersonationAuditRepository extends JpaRepository<ImpersonationAudit, Long> {

    Optional<ImpersonationAudit> findByAdmin_IdAndTargetUser_IdAndEndedAtIsNull(Long adminId, Long targetUserId);
}
