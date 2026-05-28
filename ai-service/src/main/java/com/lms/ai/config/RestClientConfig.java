package com.lms.ai.config;

import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestClientConfig {

    @Bean
    public RestClient llmRestClient(LlmProperties props, RestClient.Builder builder) {
        int timeoutMs = props.timeoutSeconds() * 1000;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return builder
                .baseUrl(props.apiBaseUrl())
                .requestFactory(factory)
                .defaultHeader("Authorization", "Bearer " + props.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    /**
     * Generation-scoped RestTemplate with a longer timeout (AI_REQUEST_TIMEOUT_SECONDS, default 60 s).
     * Distinct from the health probe's shorter timeout configured via LLM_TIMEOUT_SECONDS.
     */
    @Bean("generationRestTemplate")
    public RestTemplate generationRestTemplate(LlmProperties llm, AiProperties ai) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofMillis(ai.getRequestTimeoutSeconds() * 1000L));
        factory.setConnectTimeout(Duration.ofMillis(5_000));
        return new RestTemplate(factory);
    }
}
