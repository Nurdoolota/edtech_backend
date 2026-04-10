package com.lms.learning.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

    @Bean("aiRestClient")
    public RestClient aiRestClient(AiServiceProperties props) {
        return RestClient.builder()
                .baseUrl(props.getUrl())
                .build();
    }

    @Bean("contentRestClient")
    public RestClient contentRestClient(ContentServiceProperties props) {
        return RestClient.builder()
                .baseUrl(props.getUrl())
                .build();
    }
}
