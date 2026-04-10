package com.lms.content.service;

import com.lms.content.dto.PagedResponse;
import com.lms.content.dto.group.CreateGroupRequest;
import com.lms.content.dto.group.GroupResponse;
import com.lms.content.dto.group.UpdateGroupRequest;
import com.lms.content.entity.Group;
import com.lms.content.entity.GroupCourse;
import com.lms.content.entity.UserGroup;
import com.lms.content.entity.UserGroupId;
import com.lms.content.entity.GroupCourseId;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.repository.GroupCourseRepository;
import com.lms.content.repository.GroupRepository;
import com.lms.content.repository.UserGroupRepository;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.security.RoleName;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserGroupRepository userGroupRepository;
    private final GroupCourseRepository groupCourseRepository;
    private final CourseRepository courseRepository;
    private final ContentMapper mapper;

    public GroupService(GroupRepository groupRepository, UserGroupRepository userGroupRepository,
            GroupCourseRepository groupCourseRepository, CourseRepository courseRepository,
            ContentMapper mapper) {
        this.groupRepository = groupRepository;
        this.userGroupRepository = userGroupRepository;
        this.groupCourseRepository = groupCourseRepository;
        this.courseRepository = courseRepository;
        this.mapper = mapper;
    }

    public PagedResponse<GroupResponse> findAll(JwtUserPrincipal principal, Pageable pageable) {
        if (principal.isStudent()) {
            throw ApiBusinessException.forbidden();
        }
        Page<Group> page = principal.isAdmin()
                ? groupRepository.findAll(pageable)
                : groupRepository.findAllByTeacherId(principal.getUserId(), pageable);
        return PagedResponse.from(page.map(mapper::toGroupResponse));
    }

    public GroupResponse findById(Long id, JwtUserPrincipal principal) {
        Group group = loadGroup(id);
        checkAccess(group, principal);
        return mapper.toGroupResponse(group);
    }

    @Transactional
    public GroupResponse create(CreateGroupRequest req, JwtUserPrincipal principal) {
        if (principal.isStudent()) {
            throw ApiBusinessException.forbidden();
        }
        Group group = new Group();
        group.setName(req.name());
        group.setTeacherId(principal.getUserId());
        return mapper.toGroupResponse(groupRepository.save(group));
    }

    @Transactional
    public GroupResponse update(Long id, UpdateGroupRequest req, JwtUserPrincipal principal) {
        Group group = loadGroup(id);
        checkWriteAccess(group, principal);
        group.setName(req.name());
        return mapper.toGroupResponse(groupRepository.save(group));
    }

    @Transactional
    public void delete(Long id, JwtUserPrincipal principal) {
        Group group = loadGroup(id);
        checkWriteAccess(group, principal);
        groupRepository.delete(group);
    }

    @Transactional
    public void addStudent(Long groupId, Long userId, JwtUserPrincipal principal) {
        Group group = loadGroup(groupId);
        checkWriteAccess(group, principal);
        if (userGroupRepository.existsByIdGroupIdAndIdUserId(groupId, userId)) {
            throw ApiBusinessException.conflict("User " + userId + " is already in group " + groupId);
        }
        userGroupRepository.save(new UserGroup(userId, groupId));
    }

    @Transactional
    public void removeStudent(Long groupId, Long userId, JwtUserPrincipal principal) {
        Group group = loadGroup(groupId);
        checkWriteAccess(group, principal);
        UserGroupId key = new UserGroupId(userId, groupId);
        if (!userGroupRepository.existsById(key)) {
            throw ApiBusinessException.notFound("UserGroup", userId);
        }
        userGroupRepository.deleteById(key);
    }

    @Transactional
    public void assignCourse(Long groupId, Long courseId, JwtUserPrincipal principal) {
        Group group = loadGroup(groupId);
        checkWriteAccess(group, principal);
        if (!courseRepository.existsById(courseId)) {
            throw ApiBusinessException.notFound("Course", courseId);
        }
        if (groupCourseRepository.existsByIdGroupIdAndIdCourseId(groupId, courseId)) {
            throw ApiBusinessException.conflict(
                    "Course " + courseId + " is already assigned to group " + groupId);
        }
        groupCourseRepository.save(new GroupCourse(groupId, courseId));
    }

    @Transactional
    public void removeCourse(Long groupId, Long courseId, JwtUserPrincipal principal) {
        Group group = loadGroup(groupId);
        checkWriteAccess(group, principal);
        GroupCourseId key = new GroupCourseId(groupId, courseId);
        if (!groupCourseRepository.existsById(key)) {
            throw ApiBusinessException.notFound("GroupCourse", courseId);
        }
        groupCourseRepository.deleteById(key);
    }

    private Group loadGroup(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> ApiBusinessException.notFound("Group", id));
    }

    private void checkAccess(Group group, JwtUserPrincipal principal) {
        if (principal.isAdmin()) return;
        if (principal.isStudent()) throw ApiBusinessException.forbidden();
        if (!group.getTeacherId().equals(principal.getUserId())) {
            throw ApiBusinessException.forbidden();
        }
    }

    private void checkWriteAccess(Group group, JwtUserPrincipal principal) {
        if (principal.isAdmin()) return;
        if (principal.getRole() != RoleName.TEACHER
                || !group.getTeacherId().equals(principal.getUserId())) {
            throw ApiBusinessException.forbidden();
        }
    }
}
