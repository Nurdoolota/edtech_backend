package com.lms.content.service;

import com.lms.content.dto.course.CourseResponse;
import com.lms.content.dto.group.GroupResponse;
import com.lms.content.dto.task.TaskResponse;
import com.lms.content.entity.Course;
import com.lms.content.entity.Group;
import com.lms.content.entity.Task;
import org.springframework.stereotype.Component;

@Component
public class ContentMapper {

    public CourseResponse toCourseResponse(Course c) {
        return new CourseResponse(c.getId(), c.getTitle(), c.getDescription(),
                c.getAuthorId(), c.getCreatedAt(), c.getUpdatedAt());
    }

    public TaskResponse toTaskResponse(Task t) {
        return new TaskResponse(t.getId(), t.getCourseId(), t.getType(), t.getContent(),
                t.getPromptTemplateId(), t.getCreatedAt(), t.getUpdatedAt());
    }

    public GroupResponse toGroupResponse(Group g) {
        return new GroupResponse(g.getId(), g.getName(), g.getTeacherId(), g.getCreatedAt());
    }
}
