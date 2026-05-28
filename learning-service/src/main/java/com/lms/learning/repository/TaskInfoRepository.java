package com.lms.learning.repository;

import com.lms.learning.entity.TaskInfo;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskInfoRepository extends JpaRepository<TaskInfo, Long> {

    List<TaskInfo> findByLessonIdAndOrderIndexLessThan(Long lessonId, int orderIndex);
}
