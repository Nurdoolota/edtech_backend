package com.lms.ai.llm.openai;

import com.lms.ai.config.LlmProperties;
import com.lms.ai.exception.ApiBusinessException;
import com.lms.ai.llm.LlmClient;
import com.lms.ai.llm.openai.dto.ChatRequest;
import com.lms.ai.llm.openai.dto.ChatRequest.Message;
import com.lms.ai.llm.openai.dto.ChatResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class OpenAiCompatibleClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleClient.class);

    private final RestClient restClient;
    private final LlmProperties props;

    public OpenAiCompatibleClient(RestClient llmRestClient, LlmProperties props) {
        this.restClient = llmRestClient;
        this.props = props;
    }

    @Override
    public String complete(String systemPrompt, double temperature, int maxTokens) {
        ChatRequest request = new ChatRequest(
                props.model(),
                List.of(new Message("system", systemPrompt)),
                temperature,
                maxTokens);

        ChatResponse response;
        long start = System.currentTimeMillis();
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(ChatResponse.class);
        } catch (RestClientException ex) {
            log.error("LLM request failed [model={}]: {}", props.model(), ex.getMessage());
            throw ApiBusinessException.serviceUnavailable("LLM provider is unavailable. Please try again later.");
        }

        long elapsed = System.currentTimeMillis() - start;

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw ApiBusinessException.serviceUnavailable("LLM returned an empty response.");
        }

        if (response.usage() != null) {
            log.info("LLM call completed [model={}, promptTokens={}, completionTokens={}, latencyMs={}]",
                    props.model(),
                    response.usage().promptTokens(),
                    response.usage().completionTokens(),
                    elapsed);
        }

        String content = response.choices().get(0).message().content();
        if (content == null || content.isBlank()) {
            throw ApiBusinessException.serviceUnavailable("LLM returned blank content.");
        }
        return content;
    }
}
