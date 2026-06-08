package com.lms.content.repository;

import com.lms.content.entity.CourseTeacher;
import com.lms.content.entity.CourseTeacherId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CourseTeacherRepository extends JpaRepository<CourseTeacher, CourseTeacherId> {

    boolean existsByIdCourseIdAndIdUserId(Long courseId, Long userId);

    List<CourseTeacher> findByIdCourseId(Long courseId);
}
