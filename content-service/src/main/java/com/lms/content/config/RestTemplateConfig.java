package com.lms.content.config;

import java.time.Duration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        // connectTimeout/readTimeout were removed from RestTemplateBuilder in Spring Boot 3.2;
        // timeouts are now configured at the ClientHttpRequestFactory level.
        return builder.build();
    }
}
