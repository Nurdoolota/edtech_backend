package com.lms.content.dto.export;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;

public class ManifestDto {
    private String formatVersion;
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant exportedAt;
    private Long sourceCourseId;
    private String checksum;

    public static ManifestDto of(Long sourceCourseId, String checksum) {
        ManifestDto dto = new ManifestDto();
        dto.formatVersion = "1.0";
        dto.exportedAt = Instant.now();
        dto.sourceCourseId = sourceCourseId;
        dto.checksum = checksum;
        return dto;
    }

    public String getFormatVersion() { return formatVersion; }
    public void setFormatVersion(String formatVersion) { this.formatVersion = formatVersion; }
    public Instant getExportedAt() { return exportedAt; }
    public void setExportedAt(Instant exportedAt) { this.exportedAt = exportedAt; }
    public Long getSourceCourseId() { return sourceCourseId; }
    public void setSourceCourseId(Long sourceCourseId) { this.sourceCourseId = sourceCourseId; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
}
