package com.lms.content.repository;

import com.lms.content.entity.Course;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

    Page<Course> findAllByAuthorId(Long authorId, Pageable pageable);

    /** Courses assigned to any group the student belongs to. */
    @Query("""
            SELECT DISTINCT c FROM Course c
            JOIN GroupCourse gc ON gc.id.courseId = c.id
            JOIN UserGroup ug ON ug.id.groupId = gc.id.groupId
            WHERE ug.id.userId = :userId
            """)
    Page<Course> findAllByStudentId(@Param("userId") Long userId, Pageable pageable);

    /** Whether a student has access to a course via group membership. */
    @Query("""
            SELECT COUNT(c) > 0 FROM Course c
            JOIN GroupCourse gc ON gc.id.courseId = c.id
            JOIN UserGroup ug ON ug.id.groupId = gc.id.groupId
            WHERE c.id = :courseId AND ug.id.userId = :userId
            """)
    boolean existsByIdAndStudentId(@Param("courseId") Long courseId,
            @Param("userId") Long userId);
}
