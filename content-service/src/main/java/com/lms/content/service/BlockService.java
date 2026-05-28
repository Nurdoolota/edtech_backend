package com.lms.content.service;

import com.lms.content.dto.block.BlockRequest;
import com.lms.content.dto.block.BlockResponse;
import com.lms.content.entity.Lesson;
import com.lms.content.entity.LessonBlock;
import com.lms.content.exception.ApiBusinessException;
import com.lms.content.repository.LessonBlockRepository;
import com.lms.content.repository.LessonRepository;
import com.lms.content.util.CourseAccessChecker;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class BlockService {

    private final LessonBlockRepository blockRepository;
    private final LessonRepository lessonRepository;
    private final CourseAccessChecker courseAccessChecker;
    private final BlockContentValidator validator;

    public BlockService(LessonBlockRepository blockRepository,
            LessonRepository lessonRepository,
            CourseAccessChecker courseAccessChecker,
            BlockContentValidator validator) {
        this.blockRepository = blockRepository;
        this.lessonRepository = lessonRepository;
        this.courseAccessChecker = courseAccessChecker;
        this.validator = validator;
    }

    public List<BlockResponse> listByLesson(Long lessonId, Long userId, String role) {
        Lesson lesson = loadLesson(lessonId);
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        return blockRepository.findByLessonIdOrderByOrderIndex(lessonId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public BlockResponse create(Long lessonId, BlockRequest request, Long userId, String role) {
        Lesson lesson = loadLesson(lessonId);
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        validator.validate(request.type(), request.contentJson());

        LessonBlock block = new LessonBlock();
        block.setLessonId(lessonId);
        block.setType(request.type().name());
        block.setContentJson(request.contentJson());
        block.setAiGenerated(request.aiGenerated());
        if (request.orderIndex() != null) {
            block.setOrderIndex(request.orderIndex());
        } else {
            int next = blockRepository.findMaxOrderIndexByLessonId(lessonId)
                    .map(max -> max + 1).orElse(0);
            block.setOrderIndex(next);
        }
        return toResponse(blockRepository.save(block));
    }

    @Transactional
    public BlockResponse update(Long blockId, BlockRequest request, Long userId, String role) {
        LessonBlock block = loadBlock(blockId);
        Lesson lesson = loadLesson(block.getLessonId());
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);

        if (request.type() != null) {
            block.setType(request.type().name());
        }
        if (request.contentJson() != null) {
            block.setContentJson(request.contentJson());
        }
        validator.validate(
                com.lms.content.entity.enums.BlockType.valueOf(block.getType()),
                block.getContentJson());

        if (request.orderIndex() != null) {
            block.setOrderIndex(request.orderIndex());
        }
        return toResponse(blockRepository.save(block));
    }

    @Transactional
    public void delete(Long blockId, Long userId, String role) {
        LessonBlock block = loadBlock(blockId);
        Lesson lesson = loadLesson(block.getLessonId());
        courseAccessChecker.checkAccess(lesson.getCourseId(), userId, role);
        blockRepository.delete(block);
    }

    private Lesson loadLesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> ApiBusinessException.notFound("Lesson", lessonId));
    }

    private LessonBlock loadBlock(Long blockId) {
        return blockRepository.findById(blockId)
                .orElseThrow(() -> ApiBusinessException.notFound("LessonBlock", blockId));
    }

    BlockResponse toResponse(LessonBlock b) {
        return new BlockResponse(b.getId(), b.getLessonId(), b.getOrderIndex(),
                b.getType(), b.getContentJson(), b.isAiGenerated(), b.getCreatedAt());
    }
}
