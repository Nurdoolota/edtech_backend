package com.lms.content.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.DefaultUriBuilderFactory;

@Configuration
public class AiClientConfig {

    @Bean("aiRestTemplate")
    public RestTemplate aiRestTemplate(
            @Value("${ai.service.url:http://localhost:8084}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setReadTimeout(10_000);
        factory.setConnectTimeout(5_000);
        RestTemplate rt = new RestTemplate(factory);
        rt.setUriTemplateHandler(new DefaultUriBuilderFactory(baseUrl));
        return rt;
    }
}
