package com.lms.content.service;

import com.lms.content.dto.course.CourseStatsResponse;
import com.lms.content.security.JwtUserPrincipal;
import com.lms.content.util.CourseAccessChecker;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseStatsService {

    @PersistenceContext
    private EntityManager em;

    private final CourseAccessChecker courseAccessChecker;

    public CourseStatsService(CourseAccessChecker courseAccessChecker) {
        this.courseAccessChecker = courseAccessChecker;
    }

    public CourseStatsResponse getStats(Long courseId, JwtUserPrincipal principal) {
        courseAccessChecker.loadCourseWithAccess(
                courseId, principal.getUserId(), principal.getRole().name());

        Object[] row = (Object[]) em.createNativeQuery("""
                SELECT
                    COUNT(DISTINCT tp.id)                                           AS topics_count,
                    COUNT(DISTINCT l.id)                                            AS lessons_count,
                    COUNT(DISTINCT t.id)                                            AS tasks_count,
                    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'PUBLISHED')     AS published_lessons,
                    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'DRAFT')         AS draft_lessons,
                    COUNT(DISTINCT l.id) FILTER (WHERE l.status = 'AI_GENERATED')  AS ai_generated_lessons
                FROM courses c
                LEFT JOIN topics tp ON tp.course_id = c.id
                LEFT JOIN lessons l ON l.course_id  = c.id
                LEFT JOIN tasks t   ON t.lesson_id   = l.id
                WHERE c.id = :courseId
                """)
                .setParameter("courseId", courseId)
                .getSingleResult();

        Long studentsAssigned = (Long) em.createNativeQuery("""
                SELECT COUNT(DISTINCT ug.user_id)
                FROM group_courses gc
                JOIN user_groups ug ON ug.group_id = gc.group_id
                WHERE gc.course_id = :courseId
                """)
                .setParameter("courseId", courseId)
                .getSingleResult();

        return new CourseStatsResponse(
                ((Number) row[0]).intValue(),
                ((Number) row[1]).intValue(),
                ((Number) row[2]).intValue(),
                ((Number) row[3]).intValue(),
                ((Number) row[4]).intValue(),
                ((Number) row[5]).intValue(),
                studentsAssigned.intValue());
    }
}
