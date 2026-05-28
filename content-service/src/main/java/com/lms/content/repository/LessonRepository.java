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
}
