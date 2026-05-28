package com.lms.content.service.import_;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.content.dto.ImportResultResponse;
import com.lms.content.dto.export.BlockExportDto;
import com.lms.content.dto.export.CourseExportDto;
import com.lms.content.dto.export.LessonExportDto;
import com.lms.content.dto.export.ManifestDto;
import com.lms.content.dto.export.TaskExportDto;
import com.lms.content.dto.export.TopicExportDto;
import com.lms.content.entity.Course;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.LessonBlock;
import com.lms.content.entity.Task;
import com.lms.content.entity.TaskType;
import com.lms.content.entity.Topic;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.repository.LessonBlockRepository;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TaskRepository;
import com.lms.content.repository.TopicRepository;
import com.lms.content.security.JwtUserPrincipal;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CourseImportService {

    private final ZipSecurityValidator zipSecurityValidator;
    private final CourseRepository courseRepository;
    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    public CourseImportService(ZipSecurityValidator zipSecurityValidator,
            CourseRepository courseRepository, TopicRepository topicRepository,
            LessonRepository lessonRepository, LessonBlockRepository lessonBlockRepository,
            TaskRepository taskRepository, ObjectMapper objectMapper) {
        this.zipSecurityValidator = zipSecurityValidator;
        this.courseRepository = courseRepository;
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.lessonBlockRepository = lessonBlockRepository;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportResultResponse importCourse(MultipartFile file, Long targetCourseId,
            JwtUserPrincipal principal) {
        if (principal.isStudent()) throw ApiBusinessException.forbidden();

        zipSecurityValidator.validate(file);

        Map<String, byte[]> entries = extractEntries(file);

        byte[] manifestBytes = entries.get("manifest.json");
        byte[] courseJsonBytes = entries.get("course.json");

        if (manifestBytes == null || courseJsonBytes == null) {
            throw new ApiBusinessException("INVALID_FORMAT", 400,
                    "manifest.json or course.json missing");
        }

        ManifestDto manifest;
        CourseExportDto courseDto;
        try {
            manifest = objectMapper.readValue(manifestBytes, ManifestDto.class);
            courseDto = objectMapper.readValue(courseJsonBytes, CourseExportDto.class);
        } catch (Exception e) {
            throw new ApiBusinessException("INVALID_FORMAT", 400,
                    "manifest.json or course.json could not be parsed: " + e.getMessage());
        }

        String computed = hexSha256(courseJsonBytes);
        if (!computed.equals(manifest.getChecksum())) {
            throw new ApiBusinessException("CHECKSUM_MISMATCH", 400,
                    "course.json checksum does not match manifest");
        }

        List<String> warnings = new ArrayList<>();
        if (entries.keySet().stream().anyMatch(k -> k.startsWith("media/"))) {
            warnings.add("Skipped media/ folder — not yet supported");
        }

        if (targetCourseId != null) {
            return mergeCourse(courseDto, targetCourseId, principal, warnings);
        } else {
            return copyCourse(courseDto, principal, warnings);
        }
    }

    private ImportResultResponse copyCourse(CourseExportDto dto, JwtUserPrincipal principal,
            List<String> warnings) {
        Course course = new Course();
        course.setTitle(dto.getTitle());
        course.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        course.setAuthorId(principal.getUserId());
        if (dto.getLevel() != null) course.setLevel(dto.getLevel());
        if (dto.getPublishMode() != null) course.setPublishMode(dto.getPublishMode());
        if (dto.getAccessStatus() != null) course.setAccessStatus(dto.getAccessStatus());
        course = courseRepository.save(course);

        int topicCount = 0, lessonCount = 0, taskCount = 0;
        Map<Long, Long> externalToNewTaskId = new HashMap<>();
        List<long[]> deferredPrereqs = new ArrayList<>();

        if (dto.getTopics() != null) {
            for (TopicExportDto topicDto : dto.getTopics()) {
                Topic topic = new Topic();
                topic.setCourseId(course.getId());
                topic.setTitle(topicDto.getTitle());
                topic.setDescription(topicDto.getDescription());
                topic.setOrderIndex(topicDto.getOrderIndex());
                topic = topicRepository.save(topic);
                topicCount++;

                if (topicDto.getLessons() != null) {
                    for (LessonExportDto lessonDto : topicDto.getLessons()) {
                        Lesson lesson = new Lesson();
                        lesson.setCourseId(course.getId());
                        lesson.setTopicId(topic.getId());
                        lesson.setTitle(lessonDto.getTitle());
                        if (lessonDto.getStatus() != null) lesson.setStatus(lessonDto.getStatus());
                        if (lessonDto.getUnlockMode() != null) lesson.setUnlockMode(lessonDto.getUnlockMode());
                        lesson.setVisible(lessonDto.isVisible());
                        lesson.setOrderIndex(lessonDto.getOrderIndex());
                        lesson = lessonRepository.save(lesson);
                        lessonCount++;

                        if (lessonDto.getBlocks() != null) {
                            for (BlockExportDto blockDto : lessonDto.getBlocks()) {
                                LessonBlock block = new LessonBlock();
                                block.setLessonId(lesson.getId());
                                block.setType(blockDto.getType());
                                block.setContentJson(blockDto.getContentJson());
                                block.setOrderIndex(blockDto.getOrderIndex());
                                block.setAiGenerated(blockDto.isAiGenerated());
                                lessonBlockRepository.save(block);
                            }
                        }

                        if (lessonDto.getTasks() != null) {
                            for (TaskExportDto taskDto : lessonDto.getTasks()) {
                                Task task = buildTaskFromDto(taskDto, course.getId(), lesson.getId());
                                task = taskRepository.save(task);
                                taskCount++;
                                if (taskDto.getExternalId() != null)
                                    externalToNewTaskId.put(taskDto.getExternalId(), task.getId());
                                if (taskDto.getPrerequisiteExternalId() != null)
                                    deferredPrereqs.add(new long[]{task.getId(), taskDto.getPrerequisiteExternalId()});
                            }
                        }
                    }
                }
            }
        }

        resolvePrereqs(deferredPrereqs, externalToNewTaskId);
        return new ImportResultResponse(course.getId(), topicCount, lessonCount, taskCount, warnings);
    }

    private ImportResultResponse mergeCourse(CourseExportDto dto, Long targetCourseId,
            JwtUserPrincipal principal, List<String> warnings) {
        Course course = courseRepository.findById(targetCourseId)
                .orElseThrow(() -> ApiBusinessException.notFound("Course", targetCourseId));
        if (!principal.isAdmin() && !course.getAuthorId().equals(principal.getUserId()))
            throw ApiBusinessException.forbidden();

        int topicCount = 0, lessonCount = 0, taskCount = 0;
        Map<Long, Long> externalToNewTaskId = new HashMap<>();
        List<long[]> deferredPrereqs = new ArrayList<>();

        List<Topic> existingTopics = topicRepository.findByCourseIdOrderByOrderIndex(targetCourseId);

        if (dto.getTopics() != null) {
            for (TopicExportDto topicDto : dto.getTopics()) {
                Topic topic = existingTopics.stream()
                        .filter(t -> t.getTitle().equalsIgnoreCase(topicDto.getTitle()))
                        .findFirst().orElse(null);
                if (topic == null) {
                    topic = new Topic();
                    topic.setCourseId(targetCourseId);
                }
                topic.setTitle(topicDto.getTitle());
                topic.setDescription(topicDto.getDescription());
                topic.setOrderIndex(topicDto.getOrderIndex());
                topic = topicRepository.save(topic);
                topicCount++;

                List<Lesson> existingLessons = lessonRepository.findByTopicIdOrderByOrderIndex(topic.getId());

                if (topicDto.getLessons() != null) {
                    for (LessonExportDto lessonDto : topicDto.getLessons()) {
                        Lesson lesson = existingLessons.stream()
                                .filter(l -> l.getTitle().equalsIgnoreCase(lessonDto.getTitle()))
                                .findFirst().orElse(null);
                        if (lesson == null) {
                            lesson = new Lesson();
                            lesson.setCourseId(targetCourseId);
                            lesson.setTopicId(topic.getId());
                        }
                        lesson.setTitle(lessonDto.getTitle());
                        if (lessonDto.getStatus() != null) lesson.setStatus(lessonDto.getStatus());
                        if (lessonDto.getUnlockMode() != null) lesson.setUnlockMode(lessonDto.getUnlockMode());
                        lesson.setVisible(lessonDto.isVisible());
                        lesson.setOrderIndex(lessonDto.getOrderIndex());
                        lesson = lessonRepository.save(lesson);
                        lessonCount++;

                        lessonBlockRepository.deleteAll(
                                lessonBlockRepository.findByLessonIdOrderByOrderIndex(lesson.getId()));
                        taskRepository.deleteAll(
                                taskRepository.findByLessonIdOrderByOrderIndex(lesson.getId()));

                        if (lessonDto.getBlocks() != null) {
                            for (BlockExportDto blockDto : lessonDto.getBlocks()) {
                                LessonBlock block = new LessonBlock();
                                block.setLessonId(lesson.getId());
                                block.setType(blockDto.getType());
                                block.setContentJson(blockDto.getContentJson());
                                block.setOrderIndex(blockDto.getOrderIndex());
                                block.setAiGenerated(blockDto.isAiGenerated());
                                lessonBlockRepository.save(block);
                            }
                        }

                        if (lessonDto.getTasks() != null) {
                            for (TaskExportDto taskDto : lessonDto.getTasks()) {
                                Task task = buildTaskFromDto(taskDto, targetCourseId, lesson.getId());
                                task = taskRepository.save(task);
                                taskCount++;
                                if (taskDto.getExternalId() != null)
                                    externalToNewTaskId.put(taskDto.getExternalId(), task.getId());
                                if (taskDto.getPrerequisiteExternalId() != null)
                                    deferredPrereqs.add(new long[]{task.getId(), taskDto.getPrerequisiteExternalId()});
                            }
                        }
                    }
                }
            }
        }

        resolvePrereqs(deferredPrereqs, externalToNewTaskId);
        return new ImportResultResponse(targetCourseId, topicCount, lessonCount, taskCount, warnings);
    }

    private Task buildTaskFromDto(TaskExportDto dto, Long courseId, Long lessonId) {
        Task task = new Task();
        task.setCourseId(courseId);
        task.setLessonId(lessonId);
        try {
            task.setType(TaskType.valueOf(dto.getType()));
        } catch (IllegalArgumentException e) {
            task.setType(TaskType.TEXT);
        }
        task.setTitle(dto.getTitle());
        task.setContent(dto.getContent());
        task.setOrderIndex(dto.getOrderIndex());
        if (dto.getStatus() != null) task.setStatus(dto.getStatus());
        if (dto.getUnlockMode() != null) task.setUnlockMode(dto.getUnlockMode());
        if (dto.getRequiredScore() != null) task.setRequiredScore(dto.getRequiredScore());
        return task;
    }

    private void resolvePrereqs(List<long[]> deferredPrereqs, Map<Long, Long> externalToNewTaskId) {
        for (long[] pair : deferredPrereqs) {
            Long newPrereqId = externalToNewTaskId.get(pair[1]);
            if (newPrereqId != null) {
                taskRepository.findById(pair[0]).ifPresent(t -> {
                    t.setPrerequisiteTaskId(newPrereqId);
                    taskRepository.save(t);
                });
            }
        }
    }

    private Map<String, byte[]> extractEntries(MultipartFile file) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zis.readAllBytes());
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new ApiBusinessException("INVALID_FORMAT", 400,
                    "Failed to read ZIP file: " + e.getMessage());
        }
        return entries;
    }

    private String hexSha256(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }
}
