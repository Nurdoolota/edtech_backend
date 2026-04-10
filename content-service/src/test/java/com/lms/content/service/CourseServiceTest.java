package com.lms.content.service;

import com.lms.content.dto.course.CreateCourseRequest;
import com.lms.content.dto.course.CourseResponse;
import com.lms.content.entity.Course;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.security.RoleName;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private ContentMapper mapper;

    @InjectMocks
    private CourseService courseService;

    private JwtUserPrincipal teacher;
    private JwtUserPrincipal student;
    private JwtUserPrincipal admin;
    private JwtUserPrincipal otherTeacher;

    @BeforeEach
    void setup() {
        teacher = new JwtUserPrincipal(10L, "teacher@lms.test", RoleName.TEACHER);
        student = new JwtUserPrincipal(20L, "student@lms.test", RoleName.STUDENT);
        admin = new JwtUserPrincipal(1L, "admin@lms.test", RoleName.ADMIN);
        otherTeacher = new JwtUserPrincipal(99L, "other@lms.test", RoleName.TEACHER);
    }

    @Test
    void create_asTeacher_setAuthorIdFromPrincipal() {
        CreateCourseRequest req = new CreateCourseRequest("Java Basics", "Intro course");
        Course saved = buildCourse(1L, "Java Basics", 10L);
        CourseResponse expectedResponse = new CourseResponse(1L, "Java Basics", "Intro course",
                10L, Instant.now(), Instant.now());

        when(courseRepository.save(any(Course.class))).thenReturn(saved);
        when(mapper.toCourseResponse(saved)).thenReturn(expectedResponse);

        CourseResponse result = courseService.create(req, teacher);

        assertThat(result.authorId()).isEqualTo(10L);
    }

    @Test
    void create_asStudent_throwsForbidden() {
        CreateCourseRequest req = new CreateCourseRequest("Course", null);
        assertThatThrownBy(() -> courseService.create(req, student))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void delete_ownerTeacher_succeeds() {
        Course course = buildCourse(5L, "My Course", 10L);
        when(courseRepository.findById(5L)).thenReturn(java.util.Optional.of(course));

        // Should not throw
        courseService.delete(5L, teacher);
    }

    @Test
    void delete_nonOwnerTeacher_throwsForbidden() {
        Course course = buildCourse(5L, "My Course", 10L);
        when(courseRepository.findById(5L)).thenReturn(java.util.Optional.of(course));

        assertThatThrownBy(() -> courseService.delete(5L, otherTeacher))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void delete_admin_succeeds() {
        Course course = buildCourse(5L, "My Course", 10L);
        when(courseRepository.findById(5L)).thenReturn(java.util.Optional.of(course));

        // Admin can delete any course — should not throw
        courseService.delete(5L, admin);
    }

    @Test
    void findById_notFound_throws404() {
        when(courseRepository.findById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> courseService.findById(999L, admin))
                .isInstanceOf(ApiBusinessException.class)
                .extracting("httpStatus")
                .isEqualTo(404);
    }

    private static Course buildCourse(Long id, String title, Long authorId) {
        Course c = new Course();
        c.setId(id);
        c.setTitle(title);
        c.setDescription("");
        c.setAuthorId(authorId);
        return c;
    }
}
