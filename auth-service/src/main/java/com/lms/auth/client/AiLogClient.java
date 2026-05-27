package com.lms.auth.client;

import com.lms.auth.config.AiServiceProperties;
import com.lms.auth.dto.PagedAiLogResponse;
import com.lms.auth.exception.ApiBusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

@Component
public class AiLogClient {

    private final RestTemplate restTemplate;
    private final AiServiceProperties properties;

    public AiLogClient(RestTemplate restTemplate, AiServiceProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public PagedAiLogResponse getAiLog(int page, int size, Long userId, String status) {
        URI uri = UriComponentsBuilder
                .fromHttpUrl(properties.getUrl() + "/internal/ai/log")
                .queryParam("page", page)
                .queryParam("size", size)
                .queryParamIfPresent("userId", Optional.ofNullable(userId))
                .queryParamIfPresent("status", Optional.ofNullable(status))
                .build().toUri();

        try {
            return restTemplate.getForObject(uri, PagedAiLogResponse.class);
        } catch (RestClientException e) {
            throw new ApiBusinessException(
                    "SERVICE_UNAVAILABLE",
                    HttpStatus.SERVICE_UNAVAILABLE.value(),
                    "AI service unavailable");
        }
    }
}
