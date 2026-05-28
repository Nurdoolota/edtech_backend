package com.lms.content.client;

import com.lms.content.client.dto.TaskResultSummary;
import com.lms.content.exception.ApiBusinessException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class LearningResultsClient {

    private final RestTemplate restTemplate;
    private final String learningServiceUrl;

    public LearningResultsClient(RestTemplate restTemplate,
            @Value("${learning.service.url}") String learningServiceUrl) {
        this.restTemplate = restTemplate;
        this.learningServiceUrl = learningServiceUrl;
    }

    public Map<Long, TaskResultSummary> fetchResults(Set<Long> taskIds, Long studentId) {
        String ids = taskIds.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
        String url = UriComponentsBuilder
                .fromHttpUrl(learningServiceUrl + "/internal/learning/results")
                .queryParam("taskIds", ids)
                .queryParam("studentId", studentId)
                .toUriString();
        try {
            List<TaskResultSummary> results = restTemplate.exchange(
                    url, HttpMethod.GET, null,
                    new ParameterizedTypeReference<List<TaskResultSummary>>() {})
                    .getBody();
            if (results == null) {
                throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                        "Learning service returned empty response");
            }
            return results.stream()
                    .collect(Collectors.toMap(TaskResultSummary::taskId, r -> r));
        } catch (RestClientException e) {
            throw new ApiBusinessException("SERVICE_UNAVAILABLE", 503,
                    "Learning service unavailable");
        }
    }
}
