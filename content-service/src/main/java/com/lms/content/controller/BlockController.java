package com.lms.content.controller;

import com.lms.content.dto.block.BlockRequest;
import com.lms.content.dto.block.BlockResponse;
import com.lms.content.service.BlockService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/content")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @GetMapping("/lessons/{lessonId}/blocks")
    public ResponseEntity<List<BlockResponse>> listByLesson(
            @PathVariable Long lessonId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(blockService.listByLesson(lessonId, userId, role));
    }

    @PostMapping("/lessons/{lessonId}/blocks")
    public ResponseEntity<BlockResponse> create(
            @PathVariable Long lessonId,
            @Valid @RequestBody BlockRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(blockService.create(lessonId, request, userId, role));
    }

    @PatchMapping("/blocks/{blockId}")
    public ResponseEntity<BlockResponse> update(
            @PathVariable Long blockId,
            @Valid @RequestBody BlockRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        return ResponseEntity.ok(blockService.update(blockId, request, userId, role));
    }

    @DeleteMapping("/blocks/{blockId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long blockId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Role") String role) {
        blockService.delete(blockId, userId, role);
        return ResponseEntity.noContent().build();
    }
}
