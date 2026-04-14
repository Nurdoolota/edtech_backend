package com.lms.ai.llm;

/**
 * Abstraction over LLM providers. Current implementation: OpenAI-compatible API.
 * Future providers (e.g. Anthropic) should implement this interface.
 */
public interface LlmClient {

    /**
     * Send a system prompt to the LLM and return the raw text response.
     *
     * @param systemPrompt fully rendered system prompt with injected values
     * @param temperature  sampling temperature (0.0–1.0)
     * @param maxTokens    upper token limit for the response
     * @return raw string content from the model
     */
    String complete(String systemPrompt, double temperature, int maxTokens);
}
