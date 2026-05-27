package com.lms.auth.service;

import com.lms.auth.config.AvatarProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AvatarService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/png", "image/jpeg");

    private final AvatarProperties avatarProperties;

    public AvatarService(AvatarProperties avatarProperties) {
        this.avatarProperties = avatarProperties;
    }

    /**
     * Validates and saves the uploaded avatar file for the given user.
     *
     * @param userId the ID of the user whose avatar is being updated
     * @param file   the uploaded multipart file
     * @return the public URL path to the saved avatar
     */
    public String save(Long userId, MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only PNG and JPEG allowed");
        }

        long maxBytes = (long) avatarProperties.getMaxSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "File too large. Maximum size is " + avatarProperties.getMaxSizeMb() + " MB");
        }

        String ext = "image/png".equals(contentType) ? "png" : "jpg";

        Path dir = Paths.get(avatarProperties.getStoragePath());
        try {
            Files.createDirectories(dir);
            Path dest = dir.resolve(userId + "." + ext);
            file.transferTo(dest.toFile());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store avatar file", e);
        }

        return "/static/avatars/" + userId + "." + ext;
    }
}
