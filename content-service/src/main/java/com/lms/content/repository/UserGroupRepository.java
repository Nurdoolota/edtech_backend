package com.lms.content.repository;

import com.lms.content.entity.UserGroup;
import com.lms.content.entity.UserGroupId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {

    List<UserGroup> findAllByIdGroupId(Long groupId);

    boolean existsByIdGroupIdAndIdUserId(Long groupId, Long userId);
}
