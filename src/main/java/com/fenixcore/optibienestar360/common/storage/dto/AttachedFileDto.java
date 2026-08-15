package com.fenixcore.optibienestar360.common.storage.dto;

import com.fenixcore.optibienestar360.common.storage.FileVisibility;

import java.time.Instant;
import java.util.UUID;

/** Metadata row for {@code GET .../documents} listings — never carries a presigned URL (see spec §4.1). */
public record AttachedFileDto(
        UUID uuid,
        String category,
        FileVisibility visibility,
        String fileName,
        String mimeType,
        Long sizeBytes,
        UUID uploadedByUuid,
        Instant uploadedAt,
        boolean shared,
        Instant publishedAt
) {}
