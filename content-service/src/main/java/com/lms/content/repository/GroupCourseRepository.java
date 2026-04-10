package com.lms.content.repository;

import com.lms.content.entity.GroupCourse;
import com.lms.content.entity.GroupCourseId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupCourseRepository extends JpaRepository<GroupCourse, GroupCourseId> {

    List<GroupCourse> findAllByIdGroupId(Long groupId);

    boolean existsByIdGroupIdAndIdCourseId(Long groupId, Long courseId);
}
