package com.lms.learning.repository;

import com.lms.learning.entity.TaskResult;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskResultRepository extends JpaRepository<TaskResult, Long> {

    Optional<TaskResult> findByStudentIdAndTaskId(Long studentId, Long taskId);

    Page<TaskResult> findByStudentId(Long studentId, Pageable pageable);

    @Query("SELECT tr FROM TaskResult tr WHERE tr.studentId = :studentId "
            + "AND tr.taskId IN (SELECT t.id FROM Task t WHERE t.courseId = :courseId)")
    Page<TaskResult> findByStudentIdAndCourseId(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId,
            Pageable pageable);

    Page<TaskResult> findByTaskId(Long taskId, Pageable pageable);
}
