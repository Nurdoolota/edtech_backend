package com.lms.content.repository;

import com.lms.content.entity.CourseTeacher;
import com.lms.content.entity.CourseTeacherId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseTeacherRepository extends JpaRepository<CourseTeacher, CourseTeacherId> {

    boolean existsByIdCourseIdAndIdUserId(Long courseId, Long userId);
}
