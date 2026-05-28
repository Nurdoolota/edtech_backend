package com.lms.ai.repository;

import com.lms.ai.entity.AiCallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiCallLogRepository extends JpaRepository<AiCallLog, Long> {

    Page<AiCallLog> findByUserId(Long userId, Pageable pageable);

    Page<AiCallLog> findByStatus(String status, Pageable pageable);

    Page<AiCallLog> findByUserIdAndStatus(Long userId, String status, Pageable pageable);
}
