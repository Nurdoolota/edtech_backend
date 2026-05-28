package com.lms.content.repository;

import com.lms.content.entity.Lesson;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonRepository extends JpaRepository<Lesson, Long> {

    List<Lesson> findByCourseIdOrderByOrderIndex(Long courseId);

    List<Lesson> findByTopicIdOrderByOrderIndex(Long topicId);

    @Query("SELECT MAX(l.orderIndex) FROM Lesson l WHERE l.courseId = :courseId")
    Optional<Integer> findMaxOrderIndexByCourseId(@Param("courseId") Long courseId);

    @Query("SELECT MAX(l.orderIndex) FROM Lesson l WHERE l.topicId = :topicId")
    Optional<Integer> findMaxOrderIndexByTopicId(@Param("topicId") Long topicId);

    @Query(value = """
            SELECT l.id, l.topic_id, l.title, l.status, l.order_index,
                   l.unlock_mode, l.visible,
                   COUNT(DISTINCT lb.id) AS blocks_count,
                   COUNT(DISTINCT t.id)  AS tasks_count
            FROM lessons l
            LEFT JOIN lesson_blocks lb ON lb.lesson_id = l.id
            LEFT JOIN tasks t           ON t.lesson_id  = l.id
            WHERE l.course_id = :courseId
            GROUP BY l.id
            ORDER BY l.order_index
            """, nativeQuery = true)
    List<Object[]> findLessonsWithCountsByCourseId(@Param("courseId") Long courseId);
}
