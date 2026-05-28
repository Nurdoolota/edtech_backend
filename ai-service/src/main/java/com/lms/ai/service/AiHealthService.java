package com.lms.ai.service;

import com.lms.ai.config.LlmProperties;
import com.lms.ai.dto.HealthResponse;
import com.lms.ai.llm.LlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiHealthService {

    private static final Logger log = LoggerFactory.getLogger(AiHealthService.class);

    private static final String PROBE_PROMPT = "Respond with OK.";
    private static final double PROBE_TEMPERATURE = 0.0;
    private static final int PROBE_MAX_TOKENS = 5;

    private final LlmClient llmClient;
    private final LlmProperties props;

    public AiHealthService(LlmClient llmClient, LlmProperties props) {
        this.llmClient = llmClient;
        this.props = props;
    }

    public HealthResponse probe() {
        long start = System.currentTimeMillis();
        try {
            llmClient.complete(PROBE_PROMPT, PROBE_TEMPERATURE, PROBE_MAX_TOKENS);
            int latency = (int) (System.currentTimeMillis() - start);
            return new HealthResponse("UP", props.model(), latency);
        } catch (Exception e) {
            int latency = (int) (System.currentTimeMillis() - start);
            log.warn("LLM health probe failed: {}", e.getMessage());
            return new HealthResponse("DOWN", props.model(), latency);
        }
    }
}
