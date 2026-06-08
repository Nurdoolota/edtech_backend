package com.lms.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.lms.content.client.LearningResultsClient;
import com.lms.content.client.LearningStatsClient;
import com.lms.content.dto.student.StudentCourseResponse;
import com.lms.content.dto.student.StudentCourseTreeResponse;
import com.lms.content.dto.student.StudentTaskAvailabilityResponse;
import com.lms.content.entity.Course;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.Task;
import com.lms.content.entity.TaskType;
import com.lms.content.entity.Topic;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.CourseRepository;
import com.lms.content.repository.LessonBlockRepository;
import com.lms.content.repository.LessonRepository;
import com.lms.content.repository.TaskRepository;
import com.lms.content.repository.TopicRepository;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

@ExtendWith(MockitoExtension.class)
class StudentContentServiceTest {

    @Mock CourseRepository courseRepository;
    @Mock LessonRepository lessonRepository;
    @Mock LessonBlockRepository lessonBlockRepository;
    @Mock TaskRepository taskRepository;
    @Mock TopicRepository topicRepository;
    @Mock LearningResultsClient learningResultsClient;
    @Mock LearningStatsClient learningStatsClient;

    StudentContentService service;

    @BeforeEach
    void setUp() {
        service = new StudentContentService(courseRepository, lessonRepository,
                lessonBlockRepository, taskRepository, topicRepository,
                learningResultsClient, learningStatsClient);
    }

    @Test
    void listCourses_returnsMappedList() {
        Course c2 = buildCourse(1L, "English A1", "Beginner", "OPEN");
        when(courseRepository.findAllByStudentId(eq(10L), any()))
                .thenReturn(new PageImpl<>(List.of(c2)));
        when(lessonRepository.findByCourseIdOrderByOrderIndex(1L)).thenReturn(List.of());

        List<StudentCourseResponse> result = service.listCourses(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
        assertThat(result.get(0).title()).isEqualTo("English A1");
        assertThat(result.get(0).lessonCount()).isEqualTo(0);
    }

    @Test
    void listCourses_emptyEnrollment_returnsEmptyList() {
        when(courseRepository.findAllByStudentId(eq(10L), any()))
                .thenReturn(new PageImpl<>(Collections.emptyList()));
        List<StudentCourseResponse> result = service.listCourses(10L);
        assertThat(result).isEmpty();
    }

    @Test
    void getCourseTree_notEnrolled_throwsForbidden() {
        Course c2 = buildCourse(1L, "Course", "A1", "OPEN");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c2));
        when(courseRepository.existsByIdAndStudentId(1L, 10L)).thenReturn(false);
        assertThatThrownBy(() -> service.getCourseTree(1L, 10L))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void getCourseTree_courseNotFound_throws404() {
        when(courseRepository.findById(999L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getCourseTree(999L, 10L))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getCourseTree_noTopics_returnsEmptyTopicsList() {
        Course c2 = buildCourse(1L, "Course", "A1", "OPEN");
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c2));
        when(courseRepository.existsByIdAndStudentId(1L, 10L)).thenReturn(true);
        when(topicRepository.findByCourseIdOrderByOrderIndex(1L)).thenReturn(Collections.emptyList());
        when(lessonRepository.findLessonsWithCountsByCourseId(1L)).thenReturn(Collections.emptyList());
        StudentCourseTreeResponse tree = service.getCourseTree(1L, 10L);
        assertThat(tree.id()).isEqualTo(1L);
        assertThat(tree.topics()).isEmpty();
    }

    @Test
    void getCourseTree_withTopic_returnsLessonWithCounts() {
        Course c2 = buildCourse(1L, "Course", "A1", "OPEN");
        Topic topic = buildTopic(10L, 1L, "Grammar", 0);
        Object[] lessonRow = new Object[]{1L, 10L, "Lesson 1", "PUBLISHED", 0, "FREE", true, 2L, 3L};
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c2));
        when(courseRepository.existsByIdAndStudentId(1L, 10L)).thenReturn(true);
        when(topicRepository.findByCourseIdOrderByOrderIndex(1L)).thenReturn(List.of(topic));
        when(lessonRepository.findLessonsWithCountsByCourseId(1L)).thenReturn(List.of(lessonRow));
        StudentCourseTreeResponse tree = service.getCourseTree(1L, 10L);
        assertThat(tree.topics()).hasSize(1);
        StudentCourseTreeResponse.LessonNode lessonNode = tree.topics().get(0).lessons().get(0);
        assertThat(lessonNode.id()).isEqualTo(1L);
        assertThat(lessonNode.title()).isEqualTo("Lesson 1");
        assertThat(lessonNode.locked()).isFalse();
        assertThat(lessonNode.blocksCount()).isEqualTo(2);
        assertThat(lessonNode.tasksCount()).isEqualTo(3);
    }

    @Test
    void getCourseTree_sequentialCourse_secondLessonIsLocked() {
        Course c2 = buildCourse(1L, "Course", "A1", "SEQUENTIAL");
        Topic topic = buildTopic(10L, 1L, "Grammar", 0);
        Object[] row1 = new Object[]{1L, 10L, "L1", "PUBLISHED", 0, "FREE", true, 0L, 0L};
        Object[] row2 = new Object[]{2L, 10L, "L2", "PUBLISHED", 1, "FREE", true, 0L, 0L};
        when(courseRepository.findById(1L)).thenReturn(Optional.of(c2));
        when(courseRepository.existsByIdAndStudentId(1L, 10L)).thenReturn(true);
        when(topicRepository.findByCourseIdOrderByOrderIndex(1L)).thenReturn(List.of(topic));
        when(lessonRepository.findLessonsWithCountsByCourseId(1L)).thenReturn(List.of(row1, row2));
        StudentCourseTreeResponse tree = service.getCourseTree(1L, 10L);
        List<StudentCourseTreeResponse.LessonNode> lessons = tree.topics().get(0).lessons();
        assertThat(lessons.get(0).locked()).isFalse();
        assertThat(lessons.get(1).locked()).isTrue();
    }

    @Test
    void getAvailableTasks_lessonNotFound_throws404() {
        when(lessonRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getAvailableTasks(99L, 10L))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getAvailableTasks_notEnrolled_throwsForbidden() {
        Lesson lesson = buildLesson(1L, 5L);
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
        when(courseRepository.existsByIdAndStudentId(5L, 10L)).thenReturn(false);
        assertThatThrownBy(() -> service.getAvailableTasks(1L, 10L))
                .isInstanceOf(ApiBusinessException.class)
                .hasMessageContaining("Access denied");
    }

    @Test
    void getAvailableTasks_emptyLesson_returnsEmpty() {
        Lesson lesson = buildLesson(1L, 5L);
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
        when(courseRepository.existsByIdAndStudentId(5L, 10L)).thenReturn(true);
        when(taskRepository.findByLessonIdOrderByOrderIndex(1L)).thenReturn(Collections.emptyList());
        List<StudentTaskAvailabilityResponse> result = service.getAvailableTasks(1L, 10L);
        assertThat(result).isEmpty();
    }

    @Test
    void getAvailableTasks_freeTasks_noneAreLocked() {
        Lesson lesson = buildLesson(1L, 5L);
        Task t1 = buildTask(10L, 1L, "FREE", 0);
        Task t2 = buildTask(11L, 1L, "FREE", 1);
        when(lessonRepository.findById(1L)).thenReturn(Optional.of(lesson));
        when(courseRepository.existsByIdAndStudentId(5L, 10L)).thenReturn(true);
        when(taskRepository.findByLessonIdOrderByOrderIndex(1L)).thenReturn(List.of(t1, t2));
        List<StudentTaskAvailabilityResponse> result = service.getAvailableTasks(1L, 10L);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).locked()).isFalse();
        assertThat(result.get(1).locked()).isFalse();
    }

    private Course buildCourse(Long id, String title, String level, String accessStatus) {
        Course co = new Course();
        co.setId(id);
        co.setTitle(title);
        co.setDescription("desc");
        co.setLevel(level);
        co.setAccessStatus(accessStatus);
        co.setAuthorId(1L);
        return co;
    }

    private Topic buildTopic(Long id, Long courseId, String title, int orderIndex) {
        Topic t = new Topic();
        t.setId(id);
        t.setCourseId(courseId);
        t.setTitle(title);
        t.setOrderIndex(orderIndex);
        return t;
    }

    private Lesson buildLesson(Long id, Long courseId) {
        Lesson l = new Lesson();
        l.setId(id);
        l.setCourseId(courseId);
        l.setTitle("Test Lesson");
        l.setStatus("PUBLISHED");
        l.setUnlockMode("FREE");
        return l;
    }

    private Task buildTask(Long id, Long lessonId, String unlockMode, int orderIndex) {
        Task t = new Task();
        t.setId(id);
        t.setLessonId(lessonId);
        t.setType(TaskType.FILL_BLANKS);
        t.setUnlockMode(unlockMode);
        t.setOrderIndex(orderIndex);
        return t;
    }
}
