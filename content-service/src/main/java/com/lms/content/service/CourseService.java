package com.lms.content.service;

import com.lms.content.dto.PagedResponse;
import com.lms.content.dto.course.CourseResponse;
import com.lms.content.dto.course.CreateCourseRequest;
import com.lms.content.dto.course.UpdateCourseRequest;
import com.lms.content.entity.Course;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.security.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository courseRepository;
    private final ContentMapper mapper;

    public CourseService(CourseRepository courseRepository, ContentMapper mapper) {
        this.courseRepository = courseRepository;
        this.mapper = mapper;
    }

    public PagedResponse<CourseResponse> findAll(JwtUserPrincipal principal, Pageable pageable) {
        Page<Course> page;
        if (principal.isAdmin()) {
            page = courseRepository.findAll(pageable);
        } else if (principal.isTeacher()) {
            page = courseRepository.findAllByAuthorId(principal.getUserId(), pageable);
        } else {
            page = courseRepository.findAllByStudentId(principal.getUserId(), pageable);
        }
        return PagedResponse.from(page.map(mapper::toCourseResponse));
    }

    public CourseResponse findById(Long id, JwtUserPrincipal principal) {
        Course course = loadCourse(id);
        checkReadAccess(course, principal);
        return mapper.toCourseResponse(course);
    }

    @Transactional
    public CourseResponse create(CreateCourseRequest req, JwtUserPrincipal principal) {
        if (principal.isStudent()) {
            throw ApiBusinessException.forbidden();
        }
        Course course = new Course();
        course.setTitle(req.title());
        course.setDescription(req.description() != null ? req.description() : "");
        course.setAuthorId(principal.getUserId());
        return mapper.toCourseResponse(courseRepository.save(course));
    }

    @Transactional
    public CourseResponse update(Long id, UpdateCourseRequest req, JwtUserPrincipal principal) {
        Course course = loadCourse(id);
        checkWriteAccess(course, principal);
        course.setTitle(req.title());
        course.setDescription(req.description() != null ? req.description() : "");
        return mapper.toCourseResponse(courseRepository.save(course));
    }

    @Transactional
    public void delete(Long id, JwtUserPrincipal principal) {
        Course course = loadCourse(id);
        checkWriteAccess(course, principal);
        courseRepository.delete(course);
    }

    Course loadCourse(Long id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> ApiBusinessException.notFound("Course", id));
    }

    private void checkReadAccess(Course course, JwtUserPrincipal principal) {
        if (principal.isAdmin()) return;
        if (principal.isTeacher()) {
            if (!course.getAuthorId().equals(principal.getUserId())) {
                throw ApiBusinessException.forbidden();
            }
            return;
        }
        // STUDENT: must be in a group assigned to this course
        if (!courseRepository.existsByIdAndStudentId(course.getId(), principal.getUserId())) {
            throw ApiBusinessException.forbidden();
        }
    }

    private void checkWriteAccess(Course course, JwtUserPrincipal principal) {
        if (principal.isAdmin()) return;
        if (principal.getRole() != RoleName.TEACHER
                || !course.getAuthorId().equals(principal.getUserId())) {
            throw ApiBusinessException.forbidden();
        }
    }
}
