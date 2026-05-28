package com.lms.content.service.import_;

import com.lms.content.exception.ApiBusinessException;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class ZipSecurityValidator {

    private static final int IMPORT_MAX_FILES = 5000;

    @Value("${content.import.max-size-mb:50}")
    private long importMaxSizeMb;

    @Value("${content.import.max-unzipped-mb:200}")
    private long importMaxUnzippedMb;

    public void validate(MultipartFile file) {
        if (file.getSize() > importMaxSizeMb * 1024 * 1024L) {
            throw new ApiBusinessException("BAD_REQUEST", 400,
                    "ZIP file exceeds maximum upload size of " + importMaxSizeMb + " MB");
        }

        int fileCount = 0;
        long totalUnzippedSize = 0;
        long maxUnzipped = importMaxUnzippedMb * 1024 * 1024L;

        try (ZipInputStream zis = new ZipInputStream(file.getInputStream())) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new ApiBusinessException("INVALID_ZIP", 400,
                            "Path traversal detected: " + name);
                }

                fileCount++;
                if (fileCount > IMPORT_MAX_FILES) {
                    throw new ApiBusinessException("INVALID_ZIP", 400,
                            "ZIP contains too many files (max " + IMPORT_MAX_FILES + ")");
                }

                long entrySize = entry.getSize();
                if (entrySize >= 0) {
                    totalUnzippedSize += entrySize;
                } else {
                    totalUnzippedSize += countBytesWithLimit(zis, maxUnzipped - totalUnzippedSize);
                }

                if (totalUnzippedSize > maxUnzipped) {
                    throw new ApiBusinessException("INVALID_ZIP", 400,
                            "Unzipped content exceeds " + importMaxUnzippedMb + " MB limit");
                }

                zis.closeEntry();
            }
        } catch (ApiBusinessException e) {
            throw e;
        } catch (IOException e) {
            throw new ApiBusinessException("INVALID_ZIP", 400,
                    "Failed to read ZIP file: " + e.getMessage());
        }
    }

    private long countBytesWithLimit(ZipInputStream zis, long limit) throws IOException {
        byte[] buf = new byte[8192];
        long count = 0;
        int n;
        while ((n = zis.read(buf)) > 0) {
            count += n;
            if (count > limit) return count;
        }
        return count;
    }
}
