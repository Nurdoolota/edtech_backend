package com.lms.content.service.export;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.lms.content.entity.Topic;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.repository.LessonBlockRepository;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TaskRepository;
import com.lms.content.repository.TopicRepository;
import com.lms.content.security.JwtUserPrincipal;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseExportService {

    private final CourseRepository courseRepository;
    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final TaskRepository taskRepository;
    private final ObjectMapper objectMapper;

    public CourseExportService(CourseRepository courseRepository, TopicRepository topicRepository,
            LessonRepository lessonRepository, LessonBlockRepository lessonBlockRepository,
            TaskRepository taskRepository, ObjectMapper objectMapper) {
        this.courseRepository = courseRepository;
        this.topicRepository = topicRepository;
        this.lessonRepository = lessonRepository;
        this.lessonBlockRepository = lessonBlockRepository;
        this.taskRepository = taskRepository;
        this.objectMapper = objectMapper;
    }

    public CourseExportDto buildExportDto(Long courseId, JwtUserPrincipal principal) {
        Course course = loadAndCheck(courseId, principal);
        return buildDto(course);
    }

    public void exportZip(Long courseId, JwtUserPrincipal principal, HttpServletResponse response) {
        Course course = loadAndCheck(courseId, principal);
        CourseExportDto exportDto = buildDto(course);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"course-" + courseId + ".zip\"");

        try {
            byte[] courseJsonBytes = objectMapper.writeValueAsBytes(exportDto);
            String checksum = hexSha256(courseJsonBytes);
            byte[] manifestBytes = objectMapper.writeValueAsBytes(ManifestDto.of(courseId, checksum));

            try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
                zos.putNextEntry(new ZipEntry("manifest.json"));
                zos.write(manifestBytes);
                zos.closeEntry();
                zos.putNextEntry(new ZipEntry("course.json"));
                zos.write(courseJsonBytes);
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to write ZIP response", e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private Course loadAndCheck(Long courseId, JwtUserPrincipal principal) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> ApiBusinessException.notFound("Course", courseId));
        if (principal.isStudent()) throw ApiBusinessException.forbidden();
        if (principal.isTeacher() && !course.getAuthorId().equals(principal.getUserId()))
            throw ApiBusinessException.forbidden();
        return course;
    }

    private CourseExportDto buildDto(Course course) {
        List<Topic> topics = topicRepository.findByCourseIdOrderByOrderIndex(course.getId());
        List<Lesson> allLessons = lessonRepository.findByCourseIdOrderByOrderIndex(course.getId());
        List<Task> allTasks = taskRepository.findByCourseIdOrderByOrderIndex(course.getId());

        Map<Long, List<Lesson>> lessonsByTopic = allLessons.stream()
                .filter(l -> l.getTopicId() != null)
                .collect(Collectors.groupingBy(Lesson::getTopicId));

        Map<Long, List<Task>> tasksByLesson = allTasks.stream()
                .filter(t -> t.getLessonId() != null)
                .collect(Collectors.groupingBy(Task::getLessonId));

        List<TopicExportDto> topicDtos = topics.stream()
                .map(topic -> buildTopicDto(topic, lessonsByTopic, tasksByLesson))
                .collect(Collectors.toList());

        CourseExportDto dto = new CourseExportDto();
        dto.setExternalId(course.getId());
        dto.setTitle(course.getTitle());
        dto.setDescription(course.getDescription());
        dto.setLevel(course.getLevel());
        dto.setPublishMode(course.getPublishMode());
        dto.setAccessStatus(course.getAccessStatus());
        dto.setTopics(topicDtos);
        return dto;
    }

    private TopicExportDto buildTopicDto(Topic topic, Map<Long, List<Lesson>> lessonsByTopic,
            Map<Long, List<Task>> tasksByLesson) {
        List<Lesson> lessons = lessonsByTopic.getOrDefault(topic.getId(), List.of());
        List<LessonExportDto> lessonDtos = lessons.stream()
                .map(l -> buildLessonDto(l, tasksByLesson))
                .collect(Collectors.toList());

        TopicExportDto dto = new TopicExportDto();
        dto.setExternalId(topic.getId());
        dto.setTitle(topic.getTitle());
        dto.setDescription(topic.getDescription());
        dto.setOrderIndex(topic.getOrderIndex());
        dto.setLessons(lessonDtos);
        return dto;
    }

    private LessonExportDto buildLessonDto(Lesson lesson, Map<Long, List<Task>> tasksByLesson) {
        List<LessonBlock> blocks = lessonBlockRepository.findByLessonIdOrderByOrderIndex(lesson.getId());
        List<Task> tasks = tasksByLesson.getOrDefault(lesson.getId(), List.of());

        LessonExportDto dto = new LessonExportDto();
        dto.setExternalId(lesson.getId());
        dto.setTitle(lesson.getTitle());
        dto.setStatus(lesson.getStatus());
        dto.setUnlockMode(lesson.getUnlockMode());
        dto.setVisible(lesson.isVisible());
        dto.setOrderIndex(lesson.getOrderIndex());
        dto.setBlocks(blocks.stream().map(this::toBlockDto).collect(Collectors.toList()));
        dto.setTasks(tasks.stream().map(this::toTaskDto).collect(Collectors.toList()));
        return dto;
    }

    private BlockExportDto toBlockDto(LessonBlock block) {
        BlockExportDto dto = new BlockExportDto();
        dto.setExternalId(block.getId());
        dto.setType(block.getType());
        dto.setContentJson(block.getContentJson());
        dto.setOrderIndex(block.getOrderIndex());
        dto.setAiGenerated(block.isAiGenerated());
        return dto;
    }

    private TaskExportDto toTaskDto(Task task) {
        TaskExportDto dto = new TaskExportDto();
        dto.setExternalId(task.getId());
        dto.setType(task.getType().name());
        dto.setTitle(task.getTitle());
        dto.setContent(task.getContent());
        dto.setOrderIndex(task.getOrderIndex());
        dto.setStatus(task.getStatus());
        dto.setUnlockMode(task.getUnlockMode());
        dto.setPrerequisiteExternalId(task.getPrerequisiteTaskId());
        dto.setRequiredScore(task.getRequiredScore());
        return dto;
    }

    private String hexSha256(byte[] data) throws NoSuchAlgorithmException {
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        return HexFormat.of().formatHex(hash);
    }
}
