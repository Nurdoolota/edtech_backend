package com.lms.content.repository;

import com.lms.content.entity.LessonAccess;
import com.lms.content.entity.LessonAccessId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface LessonAccessRepository extends JpaRepository<LessonAccess, LessonAccessId> {

    List<LessonAccess> findByIdLessonId(Long lessonId);

    @Transactional
    void deleteByIdLessonIdAndIdStudentId(Long lessonId, Long studentId);
}
