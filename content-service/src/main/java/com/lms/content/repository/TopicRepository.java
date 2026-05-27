package com.lms.content.repository;

import com.lms.content.entity.Topic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TopicRepository extends JpaRepository<Topic, Long> {

    List<Topic> findByCourseIdOrderByOrderIndex(Long courseId);

    @Query("SELECT MAX(t.orderIndex) FROM Topic t WHERE t.courseId = :courseId")
    Optional<Integer> findMaxOrderIndexByCourseId(@Param("courseId") Long courseId);
}
