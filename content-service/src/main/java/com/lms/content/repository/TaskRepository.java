package com.lms.content.repository;

import com.lms.content.entity.Task;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    Page<Task> findAllByCourseId(Long courseId, Pageable pageable);

    /** Tasks for a specific course, visible to a student via group membership. */
    @Query("""
            SELECT t FROM Task t
            WHERE t.courseId = :courseId
            AND EXISTS (
                SELECT 1 FROM GroupCourse gc
                JOIN UserGroup ug ON ug.id.groupId = gc.id.groupId
                WHERE gc.id.courseId = t.courseId AND ug.id.userId = :userId
            )
            """)
    Page<Task> findAllByCourseIdAndStudentId(@Param("courseId") Long courseId,
            @Param("userId") Long userId, Pageable pageable);

    /** All tasks whose course is authored by a specific teacher. */
    @Query("""
            SELECT t FROM Task t
            JOIN Course c ON c.id = t.courseId
            WHERE c.authorId = :authorId
            """)
    Page<Task> findAllByAuthorId(@Param("authorId") Long authorId, Pageable pageable);

    /** Whether a task belongs to a course authored by a given user. */
    @Query("""
            SELECT COUNT(t) > 0 FROM Task t
            JOIN Course c ON c.id = t.courseId
            WHERE t.id = :taskId AND c.authorId = :authorId
            """)
    boolean existsByIdAndAuthorId(@Param("taskId") Long taskId,
            @Param("authorId") Long authorId);
}
