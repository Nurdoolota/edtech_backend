package com.lms.ai.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.lms.ai.config.SecurityConfig;
import com.lms.ai.dto.gen.AiBlockJson;
import com.lms.ai.dto.gen.AiGenerateLessonRequest;
import com.lms.ai.dto.gen.AiGenerateTaskRequest;
import com.lms.ai.dto.gen.AiGenerateTopicRequest;
import com.lms.ai.dto.gen.AiLessonJson;
import com.lms.ai.dto.gen.AiRegenerateLessonRequest;
import com.lms.ai.dto.gen.AiTaskJson;
import com.lms.ai.dto.gen.AiTopicJson;
import com.lms.ai.exception.AiServiceException;
import com.lms.ai.service.AiGenerationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AiGenerationController.class)
@Import(SecurityConfig.class)
class AiGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiGenerationService generationService;

    // -----------------------------------------------------------------------
    // Helper factory methods
    // -----------------------------------------------------------------------

    private AiLessonJson sampleLesson() {
        ObjectNode content = objectMapper.createObjectNode();
        content.putArray("answers").add("have");
        return new AiLessonJson(
                "Present Perfect",
                "{\"model\":\"test\"}",
                List.of(new AiBlockJson("TEXT", "{\"text\":\"theory\"}", 0)),
                List.of(new AiTaskJson("FILL_BLANKS", "Fill the blank", content, 0, "FREE")));
    }

    private AiTaskJson sampleTask() {
        ObjectNode content = objectMapper.createObjectNode();
        content.put("prompt", "Describe the scene.");
        return new AiTaskJson("SPEAKING", "Airport task", content, 0, "FREE");
    }

    private AiTopicJson sampleTopic() {
        return new AiTopicJson("Future Tenses", List.of(sampleLesson()));
    }

    // -----------------------------------------------------------------------
    // POST /internal/ai/generate-lesson
    // -----------------------------------------------------------------------

    @Test
    void generateLesson_validRequest_returns200WithTitle() throws Exception {
        when(generationService.generateLesson(any(AiGenerateLessonRequest.class), isNull()))
                .thenReturn(sampleLesson());

        String body = """
                {"courseId":1,"topicId":10,"topic":"Present Perfect","level":"B1",
                 "length":"medium","taskTypes":["FILL_BLANKS"],"includeTheory":true,
                 "instructions":"UK English"}
                """;

        mockMvc.perform(post("/internal/ai/generate-lesson")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Present Perfect"))
                .andExpect(jsonPath("$.blocks").isArray())
                .andExpect(jsonPath("$.tasks").isArray());
    }

    @Test
    void generateLesson_missingRequiredField_returns400() throws Exception {
        // missing "topic"
        String body = """
                {"courseId":1,"topicId":10,"level":"B1"}
                """;

        mockMvc.perform(post("/internal/ai/generate-lesson")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateLesson_llmFails_returns502() throws Exception {
        when(generationService.generateLesson(any(AiGenerateLessonRequest.class), isNull()))
                .thenThrow(new AiServiceException("LLM call failed after retries"));

        String body = """
                {"courseId":1,"topicId":10,"topic":"Grammar","level":"B1"}
                """;

        mockMvc.perform(post("/internal/ai/generate-lesson")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_GATEWAY"));
    }

    @Test
    void generateLesson_withXUserIdHeader_passesUserIdToService() throws Exception {
        when(generationService.generateLesson(any(AiGenerateLessonRequest.class), eq(42L)))
                .thenReturn(sampleLesson());

        String body = """
                {"courseId":1,"topicId":10,"topic":"Present Perfect","level":"B1"}
                """;

        mockMvc.perform(post("/internal/ai/generate-lesson")
                        .header("X-User-Id", "42")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    // -----------------------------------------------------------------------
    // POST /internal/ai/generate-task
    // -----------------------------------------------------------------------

    @Test
    void generateTask_validRequest_returns200WithType() throws Exception {
        when(generationService.generateTask(any(AiGenerateTaskRequest.class), isNull()))
                .thenReturn(sampleTask());

        String body = """
                {"lessonId":20,"type":"SPEAKING","context":"airport check-in","level":"B1"}
                """;

        mockMvc.perform(post("/internal/ai/generate-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("SPEAKING"))
                .andExpect(jsonPath("$.content").isNotEmpty());
    }

    @Test
    void generateTask_missingLevel_returns400() throws Exception {
        String body = """
                {"lessonId":20,"type":"SPEAKING"}
                """;

        mockMvc.perform(post("/internal/ai/generate-task")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // POST /internal/ai/generate-topic
    // -----------------------------------------------------------------------

    @Test
    void generateTopic_validRequest_returns200WithTopicTitleAndLessons() throws Exception {
        when(generationService.generateTopic(any(AiGenerateTopicRequest.class), isNull()))
                .thenReturn(sampleTopic());

        String body = """
                {"courseId":1,"topicTitle":"Future Tenses",
                 "description":"Will, going to, present continuous",
                 "level":"A2","lessonCount":1}
                """;

        mockMvc.perform(post("/internal/ai/generate-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topicTitle").value("Future Tenses"))
                .andExpect(jsonPath("$.lessons").isArray())
                .andExpect(jsonPath("$.lessons[0].title").value("Present Perfect"));
    }

    @Test
    void generateTopic_missingLessonCount_returns400() throws Exception {
        String body = """
                {"courseId":1,"topicTitle":"Future Tenses","level":"A2"}
                """;

        mockMvc.perform(post("/internal/ai/generate-topic")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    // -----------------------------------------------------------------------
    // POST /internal/ai/regenerate-lesson
    // -----------------------------------------------------------------------

    @Test
    void regenerateLesson_validRequest_returns200() throws Exception {
        when(generationService.regenerateLesson(any(AiRegenerateLessonRequest.class), isNull()))
                .thenReturn(sampleLesson());

        String body = """
                {"lessonJson":"{\\"title\\":\\"old\\"}","hint":"add grammar exercises",
                 "preserveIds":[5,6],"taskTypes":["FILL_BLANKS"]}
                """;

        mockMvc.perform(post("/internal/ai/regenerate-lesson")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Present Perfect"))
                .andExpect(jsonPath("$.blocks").isArray())
                .andExpect(jsonPath("$.tasks").isArray());
    }

    @Test
    void regenerateLesson_missingLessonJson_returns400() throws Exception {
        String body = """
                {"hint":"add grammar"}
                """;

        mockMvc.perform(post("/internal/ai/regenerate-lesson")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void regenerateLesson_llmFails_returns502() throws Exception {
        when(generationService.regenerateLesson(any(AiRegenerateLessonRequest.class), isNull()))
                .thenThrow(new AiServiceException("LLM call failed after retries"));

        String body = """
                {"lessonJson":"{\\"title\\":\\"old\\"}"}
                """;

        mockMvc.perform(post("/internal/ai/regenerate-lesson")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("BAD_GATEWAY"));
    }
}
