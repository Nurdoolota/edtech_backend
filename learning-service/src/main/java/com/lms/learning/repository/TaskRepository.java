package com.lms.learning.repository;

import com.lms.learning.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("SELECT COUNT(t) FROM Task t WHERE t.courseId = :courseId")
    long countByCourseId(@Param("courseId") Long courseId);
}
