package com.lms.learning.controller;

import com.lms.learning.exception.ApiBusinessException;
import com.lms.learning.service.MediaUploadService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/learning/media")
public class MediaDownloadController {

    private final MediaUploadService mediaUploadService;

    public MediaDownloadController(MediaUploadService mediaUploadService) {
        this.mediaUploadService = mediaUploadService;
    }

    @GetMapping("/{mediaId}")
    public ResponseEntity<Resource> download(@PathVariable String mediaId) {
        Path file = mediaUploadService.resolve(mediaId);
        if (!Files.exists(file)) {
            throw new ApiBusinessException("NOT_FOUND", 404, "Media file not found: " + mediaId);
        }

        MediaType contentType = mediaId.endsWith(".webm")
                ? MediaType.parseMediaType("audio/webm")
                : MediaType.parseMediaType("audio/wav");

        return ResponseEntity.ok()
                .contentType(contentType)
                .body(new PathResource(file));
    }
}
