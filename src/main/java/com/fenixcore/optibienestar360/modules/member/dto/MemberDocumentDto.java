package com.fenixcore.optibienestar360.modules.member.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.member.entity.MemberDocument.DocumentType;

import java.time.Instant;
import java.util.UUID;

/** Metadata row for {@code GET /v1/admin/members/{uuid}/documents} — never carries a presigned URL. */
public record MemberDocumentDto(
        UUID uuid,
        @Display(Display.Kind.ENUM) DocumentType documentType,
        String fileName,
        String mimeType,
        Long sizeBytes,
        UUID uploadedByUuid,
        @Display(Display.Kind.DATETIME) Instant uploadedAt
) {}
