package com.lms.content.service;

import com.lms.content.dto.PagedResponse;
import com.lms.content.dto.task.CreateTaskRequest;
import com.lms.content.dto.task.LessonTaskRequest;
import com.lms.content.dto.task.TaskResponse;
import com.lms.content.dto.task.UpdateTaskRequest;
import com.lms.content.entity.Course;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.Task;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TaskRepository;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.security.RoleName;
import com.lms.content.util.CourseAccessChecker;
import com.lms.content.validation.ContentJsonValidator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final LessonRepository lessonRepository;
    private final CourseService courseService;
    private final ContentJsonValidator contentJsonValidator;
    private final TaskUnlockValidator unlockValidator;
    private final CourseAccessChecker courseAccessChecker;
    private final ContentMapper mapper;

    public TaskService(TaskRepository taskRepository,
            LessonRepository lessonRepository,
            CourseService courseService,
            ContentJsonValidator contentJsonValidator,
            TaskUnlockValidator unlockValidator,
            CourseAccessChecker courseAccessChecker,
            ContentMapper mapper) {
        this.taskRepository = taskRepository;
        this.lessonRepository = lessonRepository;
        this.courseService = courseService;
        this.contentJsonValidator = contentJsonValidator;
        this.unlockValidator = unlockValidator;
        this.courseAccessChecker = courseAccessChecker;
        this.mapper = mapper;
    }

    public PagedResponse<TaskResponse> findAll(JwtUserPrincipal principal, Pageable pageable) {
        if (principal.isStudent()) {
            throw ApiBusinessException.forbidden();
        }
        Page<Task> page = principal.isAdmin()
                ? taskRepository.findAll(pageable)
                : taskRepository.findAllByAuthorId(principal.getUserId(), pageable);
        return PagedResponse.from(page.map(mapper::toTaskResponse));
    }

    public PagedResponse<TaskResponse> findByCourse(Long courseId, JwtUserPrincipal principal,
            Pageable pageable) {
        Course course = courseService.loadCourse(courseId);
        Page<Task> page;
        if (principal.isAdmin()) {
            page = taskRepository.findAllByCourseId(courseId, pageable);
        } else if (principal.isTeacher()) {
            if (!course.getAuthorId().equals(principal.getUserId())) {
                throw ApiBusinessException.forbidden();
            }
            page = taskRepository.findAllByCourseId(courseId, pageable);
        } else {
            page = taskRepository.findAllByCourseIdAndStudentId(courseId,
                    principal.getUserId(), pageable);
        }
        return PagedResponse.from(page.map(mapper::toTaskResponse));
    }

    public List<TaskResponse> listByLesson(Long lessonId, Long userId, String role) {
        Lesson lesson = loadLesson(lessonId);
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        return taskRepository.findByLessonIdOrderByOrderIndex(lessonId)
                .stream().map(mapper::toTaskResponse).collect(Collectors.toList());
    }

    public TaskResponse findById(Long id, JwtUserPrincipal principal) {
        Task task = loadTask(id);
        checkReadAccess(task, principal);
        return mapper.toTaskResponse(task);
    }

    @Transactional
    public TaskResponse create(CreateTaskRequest req, JwtUserPrincipal principal) {
        if (principal.isStudent()) {
            throw ApiBusinessException.forbidden();
        }
        Course course = courseService.loadCourse(req.courseId());
        if (principal.isTeacher() && !course.getAuthorId().equals(principal.getUserId())) {
            throw ApiBusinessException.forbidden();
        }
        contentJsonValidator.validate(req.type(), req.content());
        Task task = new Task();
        task.setCourseId(req.courseId());
        task.setType(req.type());
        task.setContent(req.content());
        task.setPromptTemplateId(req.promptTemplateId());
        return mapper.toTaskResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse createInLesson(Long lessonId, LessonTaskRequest req,
            Long userId, String role) {
        Lesson lesson = loadLesson(lessonId);
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        contentJsonValidator.validate(req.type(), req.content());

        Task task = new Task();
        task.setCourseId(lesson.getCourseId());
        task.setLessonId(lessonId);
        task.setType(req.type());
        task.setContent(req.content());
        task.setTitle(req.title());
        task.setPromptTemplateId(req.promptTemplateId());

        String unlockMode = req.unlockMode() != null ? req.unlockMode() : "FREE";
        task.setUnlockMode(unlockMode);
        task.setPrerequisiteTaskId(req.prerequisiteTaskId());
        task.setRequiredScore(req.requiredScore());

        int nextIndex = taskRepository.findMaxOrderIndexByLessonId(lessonId)
                .map(max -> max + 1).orElse(0);
        task.setOrderIndex(nextIndex);

        unlockValidator.validate(task, unlockMode, req.prerequisiteTaskId(), req.requiredScore());
        return mapper.toTaskResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse update(Long id, UpdateTaskRequest req, JwtUserPrincipal principal) {
        Task task = loadTask(id);
        checkWriteAccess(task, principal);
        contentJsonValidator.validate(req.type(), req.content());

        task.setType(req.type());
        task.setContent(req.content());
        task.setPromptTemplateId(req.promptTemplateId());
        if (req.title() != null) task.setTitle(req.title());

        String unlockMode = req.unlockMode() != null ? req.unlockMode() : task.getUnlockMode();
        Long prereqId = req.prerequisiteTaskId() != null ? req.prerequisiteTaskId() : task.getPrerequisiteTaskId();
        Integer reqScore = req.requiredScore() != null ? req.requiredScore() : task.getRequiredScore();

        unlockValidator.validate(task, unlockMode, prereqId, reqScore);

        task.setUnlockMode(unlockMode);
        task.setPrerequisiteTaskId(prereqId);
        task.setRequiredScore(reqScore);

        return mapper.toTaskResponse(taskRepository.save(task));
    }

    @Transactional
    public void delete(Long id, JwtUserPrincipal principal) {
        Task task = loadTask(id);
        checkWriteAccess(task, principal);
        taskRepository.delete(task);
    }

    private Task loadTask(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> ApiBusinessException.notFound("Task", id));
    }

    private Lesson loadLesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
    }

    private void checkReadAccess(Task task, JwtUserPrincipal principal) {
        if (principal.isAdmin()) return;
        if (principal.isTeacher()) {
            if (!taskRepository.existsByIdAndAuthorId(task.getId(), principal.getUserId())) {
                throw ApiBusinessException.forbidden();
            }
            return;
        }
        Page<Task> visible = taskRepository.findAllByCourseIdAndStudentId(
                task.getCourseId(), principal.getUserId(), Pageable.unpaged());
        boolean found = visible.stream().anyMatch(t -> t.getId().equals(task.getId()));
        if (!found) {
            throw ApiBusinessException.forbidden();
        }
    }

    private void checkWriteAccess(Task task, JwtUserPrincipal principal) {
        if (principal.isAdmin()) return;
        if (principal.getRole() != RoleName.TEACHER
                || !taskRepository.existsByIdAndAuthorId(task.getId(), principal.getUserId())) {
            throw ApiBusinessException.forbidden();
        }
    }
}
