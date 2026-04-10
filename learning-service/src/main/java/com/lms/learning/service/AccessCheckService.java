package com.lms.learning.service;

import com.lms.learning.exception.ApiBusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Verifies that a student belongs to a group assigned to the course containing a task.
 * Calls Content Service internal endpoint: GET /internal/learning/access?studentId=X&taskId=Y
 *
 * Contract (Content Service must implement):
 *   GET /internal/learning/access?studentId={id}&taskId={id}
 *   200 OK  → {"hasAccess": true}
 *   200 OK  → {"hasAccess": false}   or 403/404 → student does not have access
 */
@Service
public class AccessCheckService {

    private static final Logger log = LoggerFactory.getLogger(AccessCheckService.class);

    private final RestClient contentRestClient;

    public AccessCheckService(@Qualifier("contentRestClient") RestClient contentRestClient) {
        this.contentRestClient = contentRestClient;
    }

    public void assertStudentHasAccess(Long studentId, Long taskId) {
        try {
            AccessResponse resp = contentRestClient.get()
                    .uri("/internal/learning/access?studentId={s}&taskId={t}", studentId, taskId)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        throw ApiBusinessException.forbidden();
                    })
                    .body(AccessResponse.class);

            if (resp == null || !Boolean.TRUE.equals(resp.hasAccess())) {
                throw ApiBusinessException.forbidden();
            }
        } catch (ApiBusinessException ex) {
            throw ex;
        } catch (RestClientException ex) {
            log.error("Content Service access check failed for student={} task={}: {}",
                    studentId, taskId, ex.getMessage());
            throw ApiBusinessException.serviceUnavailable(
                    "Content service temporarily unavailable.");
        }
    }

    private record AccessResponse(Boolean hasAccess) {}
}
