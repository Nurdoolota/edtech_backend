package com.lms.content.repository;

import com.lms.content.entity.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GroupRepository extends JpaRepository<Group, Long> {

    Page<Group> findAllByTeacherId(Long teacherId, Pageable pageable);
}
