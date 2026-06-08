package com.lms.learning.repository;

import com.lms.learning.entity.ResultStatus;
import com.lms.learning.entity.TaskResult;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    @Query("SELECT tr FROM TaskResult tr WHERE tr.studentId = :studentId "
            + "AND tr.status = :status")
    Page<TaskResult> findByStudentIdAndStatus(
            @Param("studentId") Long studentId,
            @Param("status") ResultStatus status,
            Pageable pageable);

    @Query("SELECT tr FROM TaskResult tr WHERE tr.studentId = :studentId "
            + "AND tr.taskId IN (SELECT t.id FROM Task t WHERE t.courseId = :courseId) "
            + "AND tr.status = :status")
    Page<TaskResult> findByStudentIdAndCourseIdAndStatus(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId,
            @Param("status") ResultStatus status,
            Pageable pageable);

    @Query("SELECT tr FROM TaskResult tr WHERE tr.studentId = :studentId "
            + "AND tr.taskId IN (SELECT t.id FROM Task t WHERE t.courseId = :courseId) "
            + "AND tr.taskId = :taskId")
    Page<TaskResult> findByStudentIdAndCourseIdAndTaskId(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId,
            @Param("taskId") Long taskId,
            Pageable pageable);

    @Query("SELECT tr FROM TaskResult tr WHERE tr.studentId = :studentId "
            + "AND tr.taskId = :taskId")
    Page<TaskResult> findByStudentIdAndTaskId(
            @Param("studentId") Long studentId,
            @Param("taskId") Long taskId,
            Pageable pageable);

    Page<TaskResult> findByTaskId(Long taskId, Pageable pageable);

    List<TaskResult> findByTaskIdInAndStudentId(Collection<Long> taskIds, Long studentId);

    long countByStudentId(Long studentId);

    @Query("SELECT AVG(r.score) FROM TaskResult r WHERE r.studentId = :studentId")
    Double findAverageScoreByStudentId(@Param("studentId") Long studentId);

    @Query("SELECT MAX(r.createdAt) FROM TaskResult r WHERE r.studentId = :studentId")
    Instant findLatestCreatedAtByStudentId(@Param("studentId") Long studentId);

    // ── Course-progress queries ───────────────────────────────────────────────

    /** Count of distinct tasks in this course that the student completed (CHECKED or VALIDATED). */
    @Query("SELECT COUNT(DISTINCT tr.taskId) FROM TaskResult tr "
            + "WHERE tr.studentId = :studentId "
            + "AND tr.taskId IN (SELECT t.id FROM Task t WHERE t.courseId = :courseId) "
            + "AND tr.status != com.lms.learning.entity.ResultStatus.SUBMITTED")
    long countCompletedTasksByCourse(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId);

    @Query("SELECT AVG(tr.score) FROM TaskResult tr "
            + "WHERE tr.studentId = :studentId "
            + "AND tr.taskId IN (SELECT t.id FROM Task t WHERE t.courseId = :courseId)")
    Double findAverageScoreByCourse(
            @Param("studentId") Long studentId,
            @Param("courseId") Long courseId);

    // ── Daily/streak queries ──────────────────────────────────────────────────

    /** Count of results created on or after a given instant. */
    @Query("SELECT COUNT(tr) FROM TaskResult tr "
            + "WHERE tr.studentId = :studentId AND tr.createdAt >= :since")
    long countByStudentIdSince(
            @Param("studentId") Long studentId,
            @Param("since") Instant since);

    /** All results created on or after a given instant, for daily breakdown. */
    @Query("SELECT tr FROM TaskResult tr "
            + "WHERE tr.studentId = :studentId AND tr.createdAt >= :since "
            + "ORDER BY tr.createdAt ASC")
    List<TaskResult> findByStudentIdSince(
            @Param("studentId") Long studentId,
            @Param("since") Instant since);
}
