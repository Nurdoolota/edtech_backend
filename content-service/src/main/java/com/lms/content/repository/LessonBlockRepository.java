package com.lms.content.repository;

import com.lms.content.entity.LessonBlock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LessonBlockRepository extends JpaRepository<LessonBlock, Long> {

    List<LessonBlock> findByLessonIdOrderByOrderIndex(Long lessonId);

    long countByLessonId(Long lessonId);

    @Query("SELECT MAX(b.orderIndex) FROM LessonBlock b WHERE b.lessonId = :lessonId")
    Optional<Integer> findMaxOrderIndexByLessonId(@Param("lessonId") Long lessonId);
}
