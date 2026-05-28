package com.lms.content.service;

import com.lms.content.dto.group.BulkAddStudentsResponse;
import com.lms.content.entity.Group;
import com.lms.content.entity.UserGroup;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.repository.GroupCourseRepository;
import com.lms.content.repository.GroupRepository;
import com.lms.content.repository.UserGroupRepository;
import com.lms.content.repository.UserSummaryRepository;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.security.RoleName;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupServiceBulkAddTest {

    @Mock private GroupRepository groupRepository;
    @Mock private UserGroupRepository userGroupRepository;
    @Mock private GroupCourseRepository groupCourseRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private UserSummaryRepository userSummaryRepository;
    @Mock private ContentMapper mapper;

    private GroupService groupService;

    private JwtUserPrincipal ownerTeacher;
    private JwtUserPrincipal otherTeacher;
    private JwtUserPrincipal admin;
    private JwtUserPrincipal student;

    private Group group;

    @BeforeEach
    void setup() {
        groupService = new GroupService(
                groupRepository, userGroupRepository, groupCourseRepository,
                courseRepository, userSummaryRepository, mapper);

        ownerTeacher = new JwtUserPrincipal(10L, "teacher@lms.test", RoleName.TEACHER);
        otherTeacher = new JwtUserPrincipal(99L, "other@lms.test", RoleName.TEACHER);
        admin        = new JwtUserPrincipal(1L,  "admin@lms.test",   RoleName.ADMIN);
        student      = new JwtUserPrincipal(20L, "student@lms.test", RoleName.STUDENT);

        group = new Group();
        group.setId(5L);
        group.setName("Group A");
        group.setTeacherId(10L);
    }

    // --- happy-path: all new, all valid students ---

    @Test
    void bulkAdd_allNewValidStudents_returnsAllInAdded() {
        List<Long> ids = List.of(42L, 43L, 44L);

        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(userSummaryRepository.findActiveStudentIds(anyCollection())).thenReturn(ids);
        when(userGroupRepository.findAllByIdGroupId(5L)).thenReturn(List.of());
        when(userGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BulkAddStudentsResponse resp = groupService.bulkAddStudents(5L, ids, ownerTeacher);

        assertThat(resp.added()).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(resp.alreadyInGroup()).isEmpty();
        assertThat(resp.notFound()).isEmpty();
        verify(userGroupRepository, times(3)).save(any(UserGroup.class));
    }

    // --- mixed: one existing member, one new, one non-existent ID ---

    @Test
    void bulkAdd_mixedIds_correctCategorisation() {
        Long existing = 42L;
        Long newStudent = 43L;
        Long unknown = 99L;

        List<Long> requestIds = List.of(existing, newStudent, unknown);
        List<Long> activeStudents = List.of(existing, newStudent); // 99 not in DB as STUDENT

        UserGroup existingMembership = new UserGroup(existing, 5L);

        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(userSummaryRepository.findActiveStudentIds(anyCollection())).thenReturn(activeStudents);
        when(userGroupRepository.findAllByIdGroupId(5L)).thenReturn(List.of(existingMembership));
        when(userGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BulkAddStudentsResponse resp = groupService.bulkAddStudents(5L, requestIds, ownerTeacher);

        assertThat(resp.added()).containsExactly(newStudent);
        assertThat(resp.alreadyInGroup()).containsExactly(existing);
        assertThat(resp.notFound()).containsExactly(unknown);
        verify(userGroupRepository, times(1)).save(any(UserGroup.class));
    }

    // --- idempotency: second call with same IDs should put all in alreadyInGroup ---

    @Test
    void bulkAdd_secondCall_allMovesToAlreadyInGroup() {
        List<Long> ids = List.of(42L, 43L);

        UserGroup ug1 = new UserGroup(42L, 5L);
        UserGroup ug2 = new UserGroup(43L, 5L);

        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(userSummaryRepository.findActiveStudentIds(anyCollection())).thenReturn(ids);
        when(userGroupRepository.findAllByIdGroupId(5L)).thenReturn(List.of(ug1, ug2));

        BulkAddStudentsResponse resp = groupService.bulkAddStudents(5L, ids, ownerTeacher);

        assertThat(resp.added()).isEmpty();
        assertThat(resp.alreadyInGroup()).containsExactlyInAnyOrder(42L, 43L);
        assertThat(resp.notFound()).isEmpty();
        verify(userGroupRepository, never()).save(any());
    }

    // --- non-owner TEACHER gets 403 ---

    @Test
    void bulkAdd_nonOwnerTeacher_throwsForbidden() {
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() ->
                groupService.bulkAddStudents(5L, List.of(42L), otherTeacher))
                .isInstanceOf(ApiBusinessException.class)
                .extracting("httpStatus").isEqualTo(403);
    }

    // --- STUDENT caller gets 403 ---

    @Test
    void bulkAdd_studentCaller_throwsForbidden() {
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() ->
                groupService.bulkAddStudents(5L, List.of(42L), student))
                .isInstanceOf(ApiBusinessException.class)
                .extracting("httpStatus").isEqualTo(403);
    }

    // --- user with TEACHER role in DB but not owner → 403 ---

    @Test
    void bulkAdd_teacherRoleUserNotOwner_throwsForbidden() {
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() ->
                groupService.bulkAddStudents(5L, List.of(10L), otherTeacher))
                .isInstanceOf(ApiBusinessException.class)
                .extracting("httpStatus").isEqualTo(403);
    }

    // --- ADMIN can add students to any group ---

    @Test
    void bulkAdd_adminCaller_succeeds() {
        List<Long> ids = List.of(55L);
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(userSummaryRepository.findActiveStudentIds(anyCollection())).thenReturn(ids);
        when(userGroupRepository.findAllByIdGroupId(5L)).thenReturn(List.of());
        when(userGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BulkAddStudentsResponse resp = groupService.bulkAddStudents(5L, ids, admin);

        assertThat(resp.added()).containsExactly(55L);
    }

    // --- empty list → 400 via @NotEmpty on DTO; service size guard fires before loadGroup ---

    @Test
    void bulkAdd_emptyList_throwsBadRequest() {
        // 201 entries triggers the size guard before any DB call
        List<Long> tooMany = Collections.nCopies(201, 1L);

        assertThatThrownBy(() ->
                groupService.bulkAddStudents(5L, tooMany, ownerTeacher))
                .isInstanceOf(ApiBusinessException.class)
                .extracting("httpStatus").isEqualTo(400);
    }

    // --- 201 entries → 400 (size guard fires before loadGroup, so no repo stub needed) ---

    @Test
    void bulkAdd_over200Entries_throwsBadRequest() {
        List<Long> tooMany = Collections.nCopies(201, 42L);

        assertThatThrownBy(() ->
                groupService.bulkAddStudents(5L, tooMany, ownerTeacher))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("200");
    }

    // --- user that exists but has TEACHER role → notFound ---

    @Test
    void bulkAdd_teacherIdInRequest_appearsInNotFound() {
        Long teacherId = 77L; // exists in users but is TEACHER, not STUDENT
        // userSummaryRepository returns empty because role ≠ STUDENT
        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(userSummaryRepository.findActiveStudentIds(anyCollection())).thenReturn(List.of());
        when(userGroupRepository.findAllByIdGroupId(5L)).thenReturn(List.of());

        BulkAddStudentsResponse resp = groupService.bulkAddStudents(5L, List.of(teacherId), ownerTeacher);

        assertThat(resp.notFound()).containsExactly(teacherId);
        assertThat(resp.added()).isEmpty();
    }

    // --- group not found → 404 ---

    @Test
    void bulkAdd_groupNotFound_throws404() {
        when(groupRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                groupService.bulkAddStudents(999L, List.of(1L), ownerTeacher))
                .isInstanceOf(ApiBusinessException.class)
                .extracting("httpStatus").isEqualTo(404);
    }

    // --- duplicate IDs in request are deduplicated ---

    @Test
    void bulkAdd_duplicateIdsInRequest_deduplicatedBeforeProcessing() {
        List<Long> idsWithDuplicates = List.of(42L, 42L, 43L);
        List<Long> activeStudents = List.of(42L, 43L);

        when(groupRepository.findById(5L)).thenReturn(Optional.of(group));
        when(userSummaryRepository.findActiveStudentIds(anyCollection())).thenReturn(activeStudents);
        when(userGroupRepository.findAllByIdGroupId(5L)).thenReturn(List.of());
        when(userGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BulkAddStudentsResponse resp = groupService.bulkAddStudents(5L, idsWithDuplicates, ownerTeacher);

        // Only 2 unique saves, not 3
        verify(userGroupRepository, times(2)).save(any(UserGroup.class));
        assertThat(resp.added()).containsExactlyInAnyOrder(42L, 43L);
    }
}
