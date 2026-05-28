package com.lms.content.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.content.client.AiGenerationClient;
import com.lms.content.client.dto.AiGenerateLessonRequest;
import com.lms.content.client.dto.AiGenerateTaskRequest;
import com.lms.content.client.dto.AiGenerateTopicRequest;
import com.lms.content.client.dto.AiLessonJson;
import com.lms.content.client.dto.AiRegenerateLessonRequest;
import com.lms.content.client.dto.AiTaskJson;
import com.lms.content.client.dto.AiTopicJson;
import com.lms.content.dto.ai.GenerateLessonRequest;
import com.lms.content.dto.ai.GenerateTaskRequest;
import com.lms.content.dto.ai.GenerateTopicRequest;
import com.lms.content.dto.ai.GenerateTopicResponse;
import com.lms.content.dto.ai.RegenerateLessonRequest;
import com.lms.content.dto.lesson.LessonResponse;
import com.lms.content.dto.task.TaskResponse;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.LessonBlock;
import com.lms.content.entity.Task;
import com.lms.content.entity.TaskType;
import com.lms.content.entity.Topic;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.LessonBlockRepository;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TaskRepository;
import com.lms.content.repository.TopicRepository;
import com.lms.content.util.CourseAccessChecker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGenerationService {

    private final AiGenerationClient aiGenerationClient;
    private final LessonRepository lessonRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final TaskRepository taskRepository;
    private final TopicRepository topicRepository;
    private final CourseAccessChecker courseAccessChecker;
    private final ObjectMapper objectMapper;

    public AiGenerationService(AiGenerationClient aiGenerationClient,
            LessonRepository lessonRepository,
            LessonBlockRepository lessonBlockRepository,
            TaskRepository taskRepository,
            TopicRepository topicRepository,
            CourseAccessChecker courseAccessChecker,
            ObjectMapper objectMapper) {
        this.aiGenerationClient = aiGenerationClient;
        this.lessonRepository = lessonRepository;
        this.lessonBlockRepository = lessonBlockRepository;
        this.taskRepository = taskRepository;
        this.topicRepository = topicRepository;
        this.courseAccessChecker = courseAccessChecker;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public LessonResponse generateLesson(GenerateLessonRequest request, Long userId, String role) {
        enforceTeacherOrAdmin(role);
        courseAccessChecker.checkAccess(request.courseId(), userId, role);

        AiGenerateLessonRequest aiRequest = new AiGenerateLessonRequest(
                request.courseId(),
                request.topicId(),
                request.topic(),
                request.level(),
                request.length(),
                request.taskTypes(),
                request.includeTheory(),
                request.instructions()
        );

        AiLessonJson aiLesson = aiGenerationClient.generateLesson(aiRequest);
        return persistLesson(aiLesson, request.courseId(), request.topicId());
    }

    @Transactional
    public TaskResponse generateTask(GenerateTaskRequest request, Long userId, String role) {
        enforceTeacherOrAdmin(role);

        Lesson lesson = lessonRepository.findById(request.lessonId())
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", request.lessonId()));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);

        AiGenerateTaskRequest aiRequest = new AiGenerateTaskRequest(
                request.lessonId(),
                request.type(),
                request.context(),
                request.level()
        );

        AiTaskJson aiTask = aiGenerationClient.generateTask(aiRequest);
        return persistTask(aiTask, lesson);
    }

    @Transactional
    public GenerateTopicResponse generateTopic(GenerateTopicRequest request,
            Long userId, String role) {
        enforceTeacherOrAdmin(role);
        courseAccessChecker.checkAccess(request.courseId(), userId, role);

        AiGenerateTopicRequest aiRequest = new AiGenerateTopicRequest(
                request.courseId(),
                request.topicTitle(),
                request.description(),
                request.level(),
                request.lessonCount()
        );

        AiTopicJson aiTopic = aiGenerationClient.generateTopic(aiRequest);

        // Create and persist the topic
        int nextTopicOrder = topicRepository.findMaxOrderIndexByCourseId(request.courseId())
                .map(max -> max + 1).orElse(0);
        Topic topic = new Topic();
        topic.setCourseId(request.courseId());
        topic.setTitle(aiTopic.topicTitle());
        topic.setDescription(request.description());
        topic.setOrderIndex(nextTopicOrder);
        topic = topicRepository.save(topic);

        // Persist each lesson with its blocks and tasks
        List<AiLessonJson> lessons = aiTopic.lessons() != null ? aiTopic.lessons() : Collections.emptyList();
        int totalTasks = 0;
        for (AiLessonJson aiLesson : lessons) {
            LessonResponse lessonResponse = persistLesson(aiLesson, request.courseId(), topic.getId());
            totalTasks += lessonResponse.tasksCount();
        }

        return new GenerateTopicResponse(topic.getId(), topic.getTitle(), lessons.size(), totalTasks);
    }

    @Transactional
    public LessonResponse regenerateLesson(Long lessonId, RegenerateLessonRequest request,
            Long userId, String role) {
        enforceTeacherOrAdmin(role);

        Lesson lesson = lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);

        // Serialize existing lesson to JSON for context
        String existingLessonJson = serializeLessonToJson(lesson);

        List<Long> preserveIds = request.preserveIds() != null ? request.preserveIds() : Collections.emptyList();

        AiRegenerateLessonRequest aiRequest = new AiRegenerateLessonRequest(
                lessonId,
                existingLessonJson,
                request.hint(),
                preserveIds,
                request.taskTypes()
        );

        AiLessonJson aiLesson = aiGenerationClient.regenerateLesson(aiRequest);

        // Replace non-preserved blocks
        Set<Long> preserveSet = Set.copyOf(preserveIds);

        List<LessonBlock> existingBlocks = lessonBlockRepository.findByLessonIdOrderByOrderIndex(lessonId);
        List<LessonBlock> blocksToDelete = existingBlocks.stream()
                .filter(b -> !preserveSet.contains(b.getId()))
                .collect(Collectors.toList());
        lessonBlockRepository.deleteAll(blocksToDelete);

        // Replace non-preserved tasks
        List<Task> existingTasks = taskRepository.findByLessonId(lessonId);
        List<Task> tasksToDelete = existingTasks.stream()
                .filter(t -> !preserveSet.contains(t.getId()))
                .collect(Collectors.toList());
        taskRepository.deleteAll(tasksToDelete);

        // Persist new blocks from ai response
        List<AiLessonJson.AiBlockJson> newBlocks = aiLesson.blocks() != null
                ? aiLesson.blocks() : Collections.emptyList();

        // Start order indices after preserved blocks
        int maxPreservedBlockOrder = existingBlocks.stream()
                .filter(b -> preserveSet.contains(b.getId()))
                .mapToInt(LessonBlock::getOrderIndex)
                .max().orElse(-1);
        int blockOrderBase = maxPreservedBlockOrder + 1;

        for (int i = 0; i < newBlocks.size(); i++) {
            AiLessonJson.AiBlockJson ab = newBlocks.get(i);
            LessonBlock block = new LessonBlock();
            block.setLessonId(lessonId);
            block.setType(ab.type());
            block.setContentJson(ab.contentJson());
            block.setOrderIndex(blockOrderBase + i);
            block.setAiGenerated(true);
            lessonBlockRepository.save(block);
        }

        // Persist new tasks from ai response
        List<AiLessonJson.AiTaskJson> newTasks = aiLesson.tasks() != null
                ? aiLesson.tasks() : Collections.emptyList();

        int maxPreservedTaskOrder = existingTasks.stream()
                .filter(t -> preserveSet.contains(t.getId()))
                .mapToInt(Task::getOrderIndex)
                .max().orElse(-1);
        int taskOrderBase = maxPreservedTaskOrder + 1;

        for (int i = 0; i < newTasks.size(); i++) {
            AiLessonJson.AiTaskJson at = newTasks.get(i);
            Task task = new Task();
            task.setLessonId(lessonId);
            task.setCourseId(lesson.getCourseId());
            task.setType(TaskType.valueOf(at.type()));
            task.setTitle(at.title());
            task.setContent(at.content());
            task.setOrderIndex(taskOrderBase + i);
            task.setStatus("AI_GENERATED");
            task.setAiGenerated(true);
            String unlockMode = at.unlockMode() != null ? at.unlockMode() : "FREE";
            task.setUnlockMode(unlockMode);
            taskRepository.save(task);
        }

        // Update lesson title and status
        if (aiLesson.title() != null) {
            lesson.setTitle(aiLesson.title());
        }
        lesson.setStatus("AI_GENERATED");
        if (aiLesson.generationMetadata() != null) {
            lesson.setGenerationMetadata(aiLesson.generationMetadata());
        }
        lesson = lessonRepository.save(lesson);

        return toLessonResponse(lesson);
    }

    // --- helpers ---

    private LessonResponse persistLesson(AiLessonJson aiLesson, Long courseId, Long topicId) {
        int nextOrder = (topicId != null)
                ? lessonRepository.findMaxOrderIndexByTopicId(topicId).map(m -> m + 1).orElse(0)
                : lessonRepository.findMaxOrderIndexByCourseId(courseId).map(m -> m + 1).orElse(0);

        Lesson lesson = new Lesson();
        lesson.setCourseId(courseId);
        lesson.setTopicId(topicId);
        lesson.setTitle(aiLesson.title() != null ? aiLesson.title() : "AI Generated Lesson");
        lesson.setStatus("AI_GENERATED");
        lesson.setOrderIndex(nextOrder);
        lesson.setGenerationMetadata(aiLesson.generationMetadata());
        lesson = lessonRepository.save(lesson);

        // Persist blocks
        List<AiLessonJson.AiBlockJson> blocks = aiLesson.blocks() != null
                ? aiLesson.blocks() : Collections.emptyList();
        for (int i = 0; i < blocks.size(); i++) {
            AiLessonJson.AiBlockJson ab = blocks.get(i);
            LessonBlock block = new LessonBlock();
            block.setLessonId(lesson.getId());
            block.setType(ab.type());
            block.setContentJson(ab.contentJson());
            block.setOrderIndex(ab.orderIndex());
            block.setAiGenerated(true);
            lessonBlockRepository.save(block);
        }

        // Persist tasks
        List<AiLessonJson.AiTaskJson> tasks = aiLesson.tasks() != null
                ? aiLesson.tasks() : Collections.emptyList();
        for (int i = 0; i < tasks.size(); i++) {
            AiLessonJson.AiTaskJson at = tasks.get(i);
            Task task = new Task();
            task.setLessonId(lesson.getId());
            task.setCourseId(courseId);
            task.setType(TaskType.valueOf(at.type()));
            task.setTitle(at.title());
            task.setContent(at.content());
            task.setOrderIndex(at.orderIndex());
            task.setStatus("AI_GENERATED");
            task.setAiGenerated(true);
            String unlockMode = at.unlockMode() != null ? at.unlockMode() : "FREE";
            task.setUnlockMode(unlockMode);
            taskRepository.save(task);
        }

        return toLessonResponse(lesson);
    }

    private TaskResponse persistTask(AiTaskJson aiTask, Lesson lesson) {
        int nextOrder = taskRepository.findMaxOrderIndexByLessonId(lesson.getId())
                .map(m -> m + 1).orElse(0);

        Task task = new Task();
        task.setLessonId(lesson.getId());
        task.setCourseId(lesson.getCourseId());
        task.setType(TaskType.valueOf(aiTask.type()));
        task.setTitle(aiTask.title());
        task.setContent(aiTask.content());
        task.setOrderIndex(nextOrder);
        task.setStatus("AI_GENERATED");
        task.setAiGenerated(true);
        String unlockMode = aiTask.unlockMode() != null ? aiTask.unlockMode() : "FREE";
        task.setUnlockMode(unlockMode);
        task = taskRepository.save(task);

        return new TaskResponse(
                task.getId(), task.getCourseId(), task.getLessonId(),
                task.getType(), task.getTitle(), task.getContent(),
                task.getStatus(), task.getOrderIndex(),
                task.getUnlockMode(), task.getPrerequisiteTaskId(), task.getRequiredScore(),
                task.isAiGenerated(), task.getPromptTemplateId(),
                task.getCreatedAt(), task.getUpdatedAt());
    }

    private LessonResponse toLessonResponse(Lesson l) {
        int blocksCount = (int) lessonBlockRepository.countByLessonId(l.getId());
        int tasksCount = taskRepository.findByLessonId(l.getId()).size();
        return new LessonResponse(
                l.getId(), l.getCourseId(), l.getTopicId(),
                l.getOrderIndex(), l.getGlobalOrder(),
                l.getTitle(), l.getStatus(),
                l.getPublishMode(), l.getUnlockMode(),
                l.isVisible(), blocksCount, tasksCount,
                l.getCreatedAt(), l.getPublishedAt());
    }

    private String serializeLessonToJson(Lesson lesson) {
        List<LessonBlock> blocks = lessonBlockRepository.findByLessonIdOrderByOrderIndex(lesson.getId());
        List<Task> tasks = taskRepository.findByLessonId(lesson.getId());

        record LessonSnapshot(Long id, String title, String status,
                List<LessonBlock> blocks, List<Task> tasks) {}

        LessonSnapshot snapshot = new LessonSnapshot(
                lesson.getId(), lesson.getTitle(), lesson.getStatus(), blocks, tasks);
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            return "{\"lessonId\":" + lesson.getId() + "}";
        }
    }

    private void enforceTeacherOrAdmin(String role) {
        if ("STUDENT".equalsIgnoreCase(role)) {
            throw ApiBusinessException.forbidden();
        }
    }
}
