package com.lms.content.util;

import com.lms.content.entity.Course;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.repository.CourseTeacherRepository;
import org.springframework.stereotype.Component;

@Component
public class CourseAccessChecker {

    private final CourseRepository courseRepository;
    private final CourseTeacherRepository courseTeacherRepository;

    public CourseAccessChecker(CourseRepository courseRepository,
            CourseTeacherRepository courseTeacherRepository) {
        this.courseRepository = courseRepository;
        this.courseTeacherRepository = courseTeacherRepository;
    }

    /**
     * Verifies that the given user has access to the course.
     * Throws ApiBusinessException (404 or 403) on failure.
     * Returns silently on success.
     */
    public void checkAccess(Long courseId, Long userId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return;
        }
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> ApiBusinessException.notFound("Course", courseId));
        if (course.getAuthorId().equals(userId)
                || courseTeacherRepository.existsByIdCourseIdAndIdUserId(courseId, userId)) {
            return;
        }
        throw ApiBusinessException.forbidden();
    }

    /**
     * Loads the course and verifies access. Returns the Course on success.
     * Throws ApiBusinessException (404 or 403) on failure.
     */
    public Course loadCourseWithAccess(Long courseId, Long userId, String role) {
        if ("ADMIN".equalsIgnoreCase(role)) {
            return courseRepository.findById(courseId)
                    .orElseThrow(() -> ApiBusinessException.notFound("Course", courseId));
        }
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> ApiBusinessException.notFound("Course", courseId));
        if (course.getAuthorId().equals(userId)
                || courseTeacherRepository.existsByIdCourseIdAndIdUserId(courseId, userId)) {
            return course;
        }
        throw ApiBusinessException.forbidden();
    }
}
