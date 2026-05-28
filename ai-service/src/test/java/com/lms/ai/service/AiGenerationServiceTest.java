package com.lms.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.ai.config.AiProperties;
import com.lms.ai.config.LlmProperties;
import com.lms.ai.dto.gen.AiGenerateLessonRequest;
import com.lms.ai.dto.gen.AiGenerateTaskRequest;
import com.lms.ai.dto.gen.AiGenerateTopicRequest;
import com.lms.ai.dto.gen.AiLessonJson;
import com.lms.ai.dto.gen.AiRegenerateLessonRequest;
import com.lms.ai.dto.gen.AiTaskJson;
import com.lms.ai.dto.gen.AiTopicJson;
import com.lms.ai.exception.AiServiceException;
import com.lms.ai.llm.LlmClient;
import com.lms.ai.prompt.PromptTemplateService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiGenerationServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private PromptTemplateService templateService;

    @Mock
    private AiCallLogger callLogger;

    private AiGenerationService service;

    private static final String MODEL = "test-model";
    private static final Long USER_ID = 10L;

    // Minimal valid JSON responses that match the DTO structures
    private static final String LESSON_JSON = """
            {
              "title": "Present Perfect",
              "generationMetadata": "{\\"model\\":\\"test\\"}",
              "blocks": [{"type":"TEXT","contentJson":"{\\"text\\":\\"hello\\"}","orderIndex":0}],
              "tasks": [{"type":"FILL_BLANKS","title":"t1","content":{"answers":["a"]},"orderIndex":0,"unlockMode":"FREE"}]
            }
            """;

    private static final String TASK_JSON = """
            {"type":"SPEAKING","title":"Airport check-in","content":{"prompt":"Describe..."},"orderIndex":0,"unlockMode":"FREE"}
            """;

    private static final String TOPIC_JSON = """
            {
              "topicTitle": "Future Tenses",
              "lessons": [
                {
                  "title": "Will",
                  "generationMetadata": "{\\"model\\":\\"test\\"}",
                  "blocks": [{"type":"TEXT","contentJson":"{\\"text\\":\\"will is used...\\"}","orderIndex":0}],
                  "tasks": [{"type":"FILL_BLANKS","title":"t","content":{"answers":["will"]},"orderIndex":0,"unlockMode":"FREE"}]
                }
              ]
            }
            """;

    @BeforeEach
    void setUp() {
        LlmProperties llmProps = new LlmProperties(
                "http://localhost:1234/v1", MODEL, "test-key", 28);
        AiProperties aiProperties = new AiProperties();
        aiProperties.setMaxRetries(1);
        JsonRepairer noOpRepairer = (raw, schema) -> { throw new com.lms.ai.exception.JsonRepairException("no repair"); };
        AiRetryExecutor retryExecutor = new AiRetryExecutor(aiProperties, noOpRepairer, new ObjectMapper());

        service = new AiGenerationService(llmClient, llmProps, templateService, retryExecutor, callLogger);
    }

    // -----------------------------------------------------------------------
    // generate-lesson
    // -----------------------------------------------------------------------

    @Test
    void generateLesson_success_returnsLessonJsonAndLogs() {
        when(templateService.render(eq("lesson_generation"), any())).thenReturn("prompt text");
        when(llmClient.complete(anyString(), anyDouble(), anyInt())).thenReturn(LESSON_JSON);

        AiGenerateLessonRequest req = new AiGenerateLessonRequest(
                1L, 10L, "Present Perfect", "B1", "medium",
                List.of("FILL_BLANKS"), true, "UK English");

        AiLessonJson result = service.generateLesson(req, USER_ID);

        assertThat(result.title()).isEqualTo("Present Perfect");
        assertThat(result.blocks()).isNotEmpty();
        assertThat(result.tasks()).isNotEmpty();

        ArgumentCaptor<AiCallEntry> logCaptor = ArgumentCaptor.forClass(AiCallEntry.class);
        verify(callLogger).log(logCaptor.capture());
        AiCallEntry entry = logCaptor.getValue();
        assertThat(entry.status()).isEqualTo("SUCCESS");
        assertThat(entry.endpoint()).isEqualTo("/internal/ai/generate-lesson");
        assertThat(entry.userId()).isEqualTo(USER_ID);
        assertThat(entry.model()).isEqualTo(MODEL);
    }

    @Test
    void generateLesson_llmFails_logsErrorAndRethrows() {
        when(templateService.render(eq("lesson_generation"), any())).thenReturn("prompt text");
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenThrow(new AiServiceException("LLM unreachable"));

        AiGenerateLessonRequest req = new AiGenerateLessonRequest(
                1L, 10L, "Topic", "A2", null, null, null, null);

        assertThatThrownBy(() -> service.generateLesson(req, USER_ID))
                .isInstanceOf(AiServiceException.class);

        ArgumentCaptor<AiCallEntry> logCaptor = ArgumentCaptor.forClass(AiCallEntry.class);
        verify(callLogger).log(logCaptor.capture());
        assertThat(logCaptor.getValue().status()).isEqualTo("ERROR");
        assertThat(logCaptor.getValue().error()).contains("LLM unreachable");
    }

    // -----------------------------------------------------------------------
    // generate-task
    // -----------------------------------------------------------------------

    @Test
    void generateTask_success_returnsTaskJsonAndLogs() {
        when(templateService.render(eq("task_generation"), any())).thenReturn("prompt text");
        when(llmClient.complete(anyString(), anyDouble(), anyInt())).thenReturn(TASK_JSON);

        AiGenerateTaskRequest req = new AiGenerateTaskRequest(20L, "SPEAKING", "airport check-in", "B1");

        AiTaskJson result = service.generateTask(req, USER_ID);

        assertThat(result.type()).isEqualTo("SPEAKING");
        assertThat(result.content()).isNotNull();

        ArgumentCaptor<AiCallEntry> logCaptor = ArgumentCaptor.forClass(AiCallEntry.class);
        verify(callLogger).log(logCaptor.capture());
        assertThat(logCaptor.getValue().status()).isEqualTo("SUCCESS");
        assertThat(logCaptor.getValue().endpoint()).isEqualTo("/internal/ai/generate-task");
    }

    // -----------------------------------------------------------------------
    // generate-topic
    // -----------------------------------------------------------------------

    @Test
    void generateTopic_success_returnsTopicJsonAndLogs() {
        when(templateService.render(eq("topic_generation"), any())).thenReturn("prompt text");
        when(llmClient.complete(anyString(), anyDouble(), anyInt())).thenReturn(TOPIC_JSON);

        AiGenerateTopicRequest req = new AiGenerateTopicRequest(
                1L, "Future Tenses", "Will, going to", "A2", 1);

        AiTopicJson result = service.generateTopic(req, USER_ID);

        assertThat(result.topicTitle()).isEqualTo("Future Tenses");
        assertThat(result.lessons()).hasSize(1);

        ArgumentCaptor<AiCallEntry> logCaptor = ArgumentCaptor.forClass(AiCallEntry.class);
        verify(callLogger).log(logCaptor.capture());
        assertThat(logCaptor.getValue().status()).isEqualTo("SUCCESS");
        assertThat(logCaptor.getValue().endpoint()).isEqualTo("/internal/ai/generate-topic");
    }

    // -----------------------------------------------------------------------
    // regenerate-lesson
    // -----------------------------------------------------------------------

    @Test
    void regenerateLesson_success_returnsLessonJsonAndLogs() {
        when(templateService.render(eq("lesson_regeneration"), any())).thenReturn("prompt text");
        when(llmClient.complete(anyString(), anyDouble(), anyInt())).thenReturn(LESSON_JSON);

        AiRegenerateLessonRequest req = new AiRegenerateLessonRequest(
                "{\"title\":\"old\"}", "add grammar exercises", List.of(5L, 6L), List.of("FILL_BLANKS"));

        AiLessonJson result = service.regenerateLesson(req, USER_ID);

        assertThat(result.title()).isEqualTo("Present Perfect");
        assertThat(result.blocks()).isNotEmpty();

        ArgumentCaptor<AiCallEntry> logCaptor = ArgumentCaptor.forClass(AiCallEntry.class);
        verify(callLogger).log(logCaptor.capture());
        assertThat(logCaptor.getValue().status()).isEqualTo("SUCCESS");
        assertThat(logCaptor.getValue().endpoint()).isEqualTo("/internal/ai/regenerate-lesson");
    }

    // -----------------------------------------------------------------------
    // Log always written — even on failure
    // -----------------------------------------------------------------------

    @Test
    void generateTask_llmFails_alwaysWritesOneLogEntry() {
        when(templateService.render(eq("task_generation"), any())).thenReturn("prompt text");
        when(llmClient.complete(anyString(), anyDouble(), anyInt()))
                .thenThrow(new AiServiceException("network error"));

        AiGenerateTaskRequest req = new AiGenerateTaskRequest(1L, "FILL_BLANKS", null, "A1");

        assertThatThrownBy(() -> service.generateTask(req, null))
                .isInstanceOf(AiServiceException.class);

        verify(callLogger, times(1)).log(any(AiCallEntry.class));
    }
}
