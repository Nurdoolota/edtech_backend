package com.lms.content.client;

import com.lms.content.client.dto.StudentStatsDto;
import com.lms.content.exception.ApiBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class LearningStatsClient {

    private final RestTemplate restTemplate;
    private final String learningServiceUrl;

    public LearningStatsClient(RestTemplate restTemplate,
            @Value("${learning.service.url}") String learningServiceUrl) {
        this.restTemplate = restTemplate;
        this.learningServiceUrl = learningServiceUrl;
    }

    public StudentStatsDto getStats(Long studentId) {
        String url = learningServiceUrl + "/internal/learning/students/" + studentId + "/stats";
        try {
            StudentStatsDto result = restTemplate.getForObject(url, StudentStatsDto.class);
            if (result == null) {
                throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                        "Learning service returned empty response");
            }
            return result;
        } catch (RestClientException e) {
            throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                    "Learning service unavailable");
        }
    }
}
