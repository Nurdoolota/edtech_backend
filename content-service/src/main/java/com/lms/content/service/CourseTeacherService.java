package com.lms.content.service;

import com.lms.content.dto.course.AddCoAuthorRequest;
import com.lms.content.dto.course.CoAuthorResponse;
import com.lms.content.entity.CourseTeacher;
import com.lms.content.entity.CourseTeacherId;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseTeacherRepository;
import com.lms.content.util.CourseAccessChecker;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseTeacherService {

    private final CourseTeacherRepository courseTeacherRepository;
    private final CourseAccessChecker courseAccessChecker;

    public CourseTeacherService(CourseTeacherRepository courseTeacherRepository,
            CourseAccessChecker courseAccessChecker) {
        this.courseTeacherRepository = courseTeacherRepository;
        this.courseAccessChecker = courseAccessChecker;
    }

    public List<CoAuthorResponse> list(Long courseId, Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        return courseTeacherRepository.findByIdCourseId(courseId).stream()
                .map(ct -> new CoAuthorResponse(ct.getId().getUserId(), ct.getRole(), ct.getAddedAt()))
                .collect(Collectors.toList());
    }

    @Transactional
    public CoAuthorResponse add(Long courseId, AddCoAuthorRequest req, Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        CourseTeacherId id = new CourseTeacherId(courseId, req.userId());
        if (courseTeacherRepository.existsById(id)) {
            throw ApiBusinessException.conflict("User is already a co-author of this course");
        }
        String effectiveRole = req.role() != null ? req.role() : "EDITOR";
        CourseTeacher ct = new CourseTeacher(courseId, req.userId(), effectiveRole);
        courseTeacherRepository.save(ct);
        return new CoAuthorResponse(ct.getId().getUserId(), ct.getRole(), ct.getAddedAt());
    }

    @Transactional
    public void remove(Long courseId, Long targetUserId, Long userId, String role) {
        courseAccessChecker.checkAccess(courseId, userId, role);
        CourseTeacherId id = new CourseTeacherId(courseId, targetUserId);
        if (!courseTeacherRepository.existsById(id)) {
            throw ApiBusinessException.notFound("CourseTeacher", targetUserId);
        }
        courseTeacherRepository.deleteById(id);
    }
}
