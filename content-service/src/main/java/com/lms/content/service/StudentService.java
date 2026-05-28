package com.lms.content.service;

import com.lms.content.client.LearningStatsClient;
import com.lms.content.client.dto.StudentStatsDto;
import com.lms.content.dto.PagedResponse;
import com.lms.content.dto.student.StudentCardResponse;
import com.lms.content.dto.student.StudentSummaryResponse;
import com.lms.content.dto.student.StudentSummaryResponse.GroupRef;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.security.JwtUserPrincipal;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class StudentService {

    @PersistenceContext
    private EntityManager em;

    private final LearningStatsClient learningStatsClient;

    public StudentService(LearningStatsClient learningStatsClient) {
        this.learningStatsClient = learningStatsClient;
    }

    public PagedResponse<StudentSummaryResponse> list(
            String q, Long groupId, Boolean notInGroup,
            Pageable pageable, JwtUserPrincipal principal) {

        if (principal.isStudent()) {
            throw ApiBusinessException.forbidden();
        }

        boolean teacherScope = principal.isTeacher();
        boolean filterNotInGroup = Boolean.TRUE.equals(notInGroup) && groupId != null;
        boolean filterInGroup = !filterNotInGroup && groupId != null;

        String whereClause = buildWhere(q, filterInGroup, filterNotInGroup, teacherScope);

        Query countQuery = em.createNativeQuery("SELECT COUNT(*) FROM users u " + whereClause);
        applyParams(countQuery, q, groupId, filterInGroup || filterNotInGroup,
                teacherScope, principal.getUserId());
        long total = ((Number) countQuery.getSingleResult()).longValue();

        Query dataQuery = em.createNativeQuery(
                "SELECT u.id, u.email, u.first_name, u.last_name, u.is_active, u.created_at"
                        + " FROM users u " + whereClause
                        + " ORDER BY u.last_name, u.first_name");
        applyParams(dataQuery, q, groupId, filterInGroup || filterNotInGroup,
                teacherScope, principal.getUserId());
        dataQuery.setFirstResult((int) pageable.getOffset());
        dataQuery.setMaxResults(pageable.getPageSize());

        @SuppressWarnings("unchecked")
        List<Object[]> rows = dataQuery.getResultList();

        List<Long> studentIds = rows.stream()
                .map(row -> ((Number) row[0]).longValue())
                .collect(Collectors.toList());

        Map<Long, List<GroupRef>> groupsByStudent = studentIds.isEmpty()
                ? Collections.emptyMap()
                : loadGroupsForStudents(studentIds);

        List<StudentSummaryResponse> content = rows.stream()
                .map(row -> toSummary(row, groupsByStudent))
                .collect(Collectors.toList());

        Page<StudentSummaryResponse> page = new PageImpl<>(content, pageable, total);
        return PagedResponse.from(page);
    }

    public StudentCardResponse getById(Long studentId, JwtUserPrincipal principal) {
        if (principal.isStudent()) {
            throw ApiBusinessException.forbidden();
        }

        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT u.id, u.email, u.first_name, u.last_name, u.is_active, u.created_at"
                        + " FROM users u"
                        + " WHERE u.id = :id"
                        + " AND u.role_id = (SELECT id FROM roles WHERE name = 'STUDENT')"
                        + " AND u.is_active = true")
                .setParameter("id", studentId)
                .getResultList();

        if (rows.isEmpty()) {
            throw ApiBusinessException.notFound("Student", studentId);
        }
        Object[] row = rows.get(0);

        if (principal.isTeacher()) {
            Boolean inGroup = (Boolean) em.createNativeQuery(
                    "SELECT EXISTS ("
                            + " SELECT 1 FROM user_groups ug"
                            + " JOIN groups g ON g.id = ug.group_id"
                            + " WHERE ug.user_id = :studentId AND g.teacher_id = :teacherId"
                            + ")")
                    .setParameter("studentId", studentId)
                    .setParameter("teacherId", principal.getUserId())
                    .getSingleResult();
            if (!Boolean.TRUE.equals(inGroup)) {
                throw ApiBusinessException.forbidden();
            }
        }

        Map<Long, List<GroupRef>> groupsByStudent = loadGroupsForStudents(List.of(studentId));
        List<GroupRef> groups = groupsByStudent.getOrDefault(studentId, Collections.emptyList());

        StudentStatsDto stats = learningStatsClient.getStats(studentId);

        String firstName = (String) row[2];
        String lastName = (String) row[3];
        return new StudentCardResponse(
                ((Number) row[0]).longValue(),
                (String) row[1],
                firstName,
                lastName,
                firstName + " " + lastName,
                (Boolean) row[4],
                groups,
                toInstant(row[5]),
                stats);
    }

    private String buildWhere(String q, boolean filterInGroup, boolean filterNotInGroup,
            boolean teacherScope) {
        List<String> conditions = new ArrayList<>();
        conditions.add("u.role_id = (SELECT id FROM roles WHERE name = 'STUDENT')");
        conditions.add("u.is_active = true");
        if (q != null && !q.isBlank()) {
            conditions.add("(LOWER(u.first_name || ' ' || u.last_name)"
                    + " LIKE LOWER('%' || :q || '%')"
                    + " OR LOWER(u.email) LIKE LOWER('%' || :q || '%'))");
        }
        if (filterInGroup) {
            conditions.add("u.id IN (SELECT user_id FROM user_groups WHERE group_id = :groupId)");
        }
        if (filterNotInGroup) {
            conditions.add(
                    "u.id NOT IN (SELECT user_id FROM user_groups WHERE group_id = :groupId)");
        }
        if (teacherScope) {
            conditions.add("u.id IN ("
                    + "SELECT ug.user_id FROM user_groups ug"
                    + " JOIN groups g ON g.id = ug.group_id"
                    + " WHERE g.teacher_id = :userId)");
        }
        return "WHERE " + String.join(" AND ", conditions);
    }

    private void applyParams(Query query, String q, Long groupId, boolean needGroupId,
            boolean teacherScope, Long userId) {
        if (q != null && !q.isBlank()) {
            query.setParameter("q", q);
        }
        if (needGroupId && groupId != null) {
            query.setParameter("groupId", groupId);
        }
        if (teacherScope) {
            query.setParameter("userId", userId);
        }
    }

    private Map<Long, List<GroupRef>> loadGroupsForStudents(List<Long> studentIds) {
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery(
                "SELECT ug.user_id, g.id, g.name"
                        + " FROM user_groups ug"
                        + " JOIN groups g ON g.id = ug.group_id"
                        + " WHERE ug.user_id IN (:studentIds)")
                .setParameter("studentIds", studentIds)
                .getResultList();

        Map<Long, List<GroupRef>> result = new HashMap<>();
        for (Object[] row : rows) {
            Long uid = ((Number) row[0]).longValue();
            Long gid = ((Number) row[1]).longValue();
            String name = (String) row[2];
            result.computeIfAbsent(uid, k -> new ArrayList<>()).add(new GroupRef(gid, name));
        }
        return result;
    }

    private StudentSummaryResponse toSummary(Object[] row, Map<Long, List<GroupRef>> groupsByStudent) {
        Long id = ((Number) row[0]).longValue();
        String firstName = (String) row[2];
        String lastName = (String) row[3];
        return new StudentSummaryResponse(
                id,
                (String) row[1],
                firstName,
                lastName,
                firstName + " " + lastName,
                (Boolean) row[4],
                groupsByStudent.getOrDefault(id, Collections.emptyList()),
                toInstant(row[5]));
    }

    private Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (value instanceof Instant inst) return inst;
        return null;
    }
}
