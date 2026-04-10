package com.lms.content.service;

import com.lms.content.dto.PagedResponse;
import com.lms.content.dto.task.CreateTaskRequest;
import com.lms.content.dto.task.TaskResponse;
import com.lms.content.dto.task.UpdateTaskRequest;
import com.lms.content.entity.Course;
import com.lms.content.entity.Task;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.TaskRepository;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.security.RoleName;
import com.lms.content.validation.ContentJsonValidator;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final CourseService courseService;
    private final ContentJsonValidator contentJsonValidator;
    private final ContentMapper mapper;

    public TaskService(TaskRepository taskRepository, CourseService courseService,
            ContentJsonValidator contentJsonValidator, ContentMapper mapper) {
        this.taskRepository = taskRepository;
        this.courseService = courseService;
        this.contentJsonValidator = contentJsonValidator;
        this.mapper = mapper;
    }

    /** List all tasks (cross-course). ADMIN sees all, TEACHER sees own courses, STUDENT: forbidden. */
    public PagedResponse<TaskResponse> findAll(JwtUserPrincipal principal, Pageable pageable) {
        if (principal.isStudent()) {
            throw ApiBusinessException.forbidden();
        }
        Page<Task> page = principal.isAdmin()
                ? taskRepository.findAll(pageable)
                : taskRepository.findAllByAuthorId(principal.getUserId(), pageable);
        return PagedResponse.from(page.map(mapper::toTaskResponse));
    }

    /** List tasks for a specific course (the "topics" endpoint). */
    public PagedResponse<TaskResponse> findByCourse(Long courseId, JwtUserPrincipal principal,
            Pageable pageable) {
        // Verify the course exists (throws 404 if not)
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
    public TaskResponse update(Long id, UpdateTaskRequest req, JwtUserPrincipal principal) {
        Task task = loadTask(id);
        checkWriteAccess(task, principal);
        contentJsonValidator.validate(req.type(), req.content());
        task.setType(req.type());
        task.setContent(req.content());
        task.setPromptTemplateId(req.promptTemplateId());
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

    private void checkReadAccess(Task task, JwtUserPrincipal principal) {
        if (principal.isAdmin()) return;
        if (principal.isTeacher()) {
            if (!taskRepository.existsByIdAndAuthorId(task.getId(), principal.getUserId())) {
                throw ApiBusinessException.forbidden();
            }
            return;
        }
        // STUDENT: course must be accessible via group
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
