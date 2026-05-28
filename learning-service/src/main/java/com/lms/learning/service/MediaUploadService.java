package com.lms.learning.service;

import com.lms.learning.config.MediaProperties;
import com.lms.learning.exception.ApiBusinessException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaUploadService {

    private static final Set<String> ALLOWED = Set.of("audio/webm", "audio/wav");

    private final MediaProperties mediaProperties;

    public MediaUploadService(MediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    public String upload(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType)) {
            throw new ApiBusinessException("INVALID_MEDIA_TYPE", 400,
                    "Only audio/webm and audio/wav are accepted");
        }
        if (file.getSize() > mediaProperties.getMaxSizeBytes()) {
            throw new ApiBusinessException("FILE_TOO_LARGE", 400,
                    "File exceeds maximum allowed size of 20 MB");
        }

        String ext = contentType.equals("audio/webm") ? "webm" : "wav";
        String mediaId = UUID.randomUUID().toString() + "." + ext;
        Path target = Paths.get(mediaProperties.getStoragePath()).resolve(mediaId);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException e) {
            throw new ApiBusinessException("STORAGE_ERROR", 500,
                    "Failed to store media file: " + e.getMessage());
        }

        return mediaId;
    }

    public Path resolve(String mediaId) {
        return Paths.get(mediaProperties.getStoragePath()).resolve(mediaId);
    }
}
