package com.lms.content.repository;

import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only access to the shared {@code users} table for resolving active student IDs.
 * Content-service does not own the User domain; it only queries the minimum required
 * subset via a native SQL projection.
 */
@Repository
public class UserSummaryRepository {

    private final JdbcTemplate jdbc;

    public UserSummaryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Returns the subset of {@code ids} that correspond to active users whose role name
     * is {@code STUDENT}.
     *
     * @param ids candidate user IDs
     * @return list of IDs that exist in {@code users} with role STUDENT and {@code is_active = true}
     */
    public List<Long> findActiveStudentIds(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        // Build a parameterised IN clause
        String placeholders = ids.stream()
                .map(id -> "?")
                .reduce((a, b) -> a + "," + b)
                .orElse("?");
        String sql = """
                SELECT u.id
                FROM users u
                JOIN roles r ON r.id = u.role_id
                WHERE u.id IN (%s)
                  AND r.name = 'STUDENT'
                  AND u.is_active = true
                """.formatted(placeholders);
        return jdbc.queryForList(sql, Long.class, ids.toArray());
    }
}
