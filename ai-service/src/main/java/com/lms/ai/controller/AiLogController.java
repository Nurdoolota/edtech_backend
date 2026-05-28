package com.lms.ai.controller;

import com.lms.ai.dto.AiCallLogResponse;
import com.lms.ai.dto.PagedAiLogResponse;
import com.lms.ai.entity.AiCallLog;
import com.lms.ai.repository.AiCallLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal endpoint consumed by auth-service admin proxy (AUTH-07).
 * Not exposed through the gateway — only reachable via service-to-service calls.
 */
@RestController
public class AiLogController {

    private final AiCallLogRepository repository;

    public AiLogController(AiCallLogRepository repository) {
        this.repository = repository;
    }

    /**
     * Returns a paginated, descending-by-date view of the AI call log.
     * Optional filters: {@code userId} and {@code status}.
     */
    @GetMapping("/internal/ai/log")
    @Transactional(readOnly = true)
    public PagedAiLogResponse getLog(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String status
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<AiCallLog> result;
        if (userId != null && status != null) {
            result = repository.findByUserIdAndStatus(userId, status, pageable);
        } else if (userId != null) {
            result = repository.findByUserId(userId, pageable);
        } else if (status != null) {
            result = repository.findByStatus(status, pageable);
        } else {
            result = repository.findAll(pageable);
        }

        return new PagedAiLogResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.getNumber(),
                result.getSize()
        );
    }

    private AiCallLogResponse toResponse(AiCallLog log) {
        return new AiCallLogResponse(
                log.getId(),
                log.getRequestId(),
                log.getUserId(),
                log.getEndpoint(),
                log.getModel(),
                log.getLatencyMs(),
                log.getTokensIn(),
                log.getTokensOut(),
                log.getStatus(),
                log.getError(),
                log.getCreatedAt()
        );
    }
}
