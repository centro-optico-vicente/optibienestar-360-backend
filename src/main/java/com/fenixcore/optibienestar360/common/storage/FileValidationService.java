package com.fenixcore.optibienestar360.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Validates an upload's extension and size against the DB-configurable
 * policy for its {@link FileVisibility} scope — spec §2.1, migration V55.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileValidationService {

    private final FileTypePolicyRepository policyRepository;

    public void validate(FileVisibility visibility, MultipartFile file) {
        if (file == null || file.isEmpty()) return;
        validate(visibility, file.getOriginalFilename(), file.getSize());
    }

    public void validate(FileVisibility visibility, String fileName, long sizeBytes) {
        FileTypePolicy policy = policyRepository.findByVisibility(visibility)
                .orElseThrow(() -> new NoSuchElementException("file.type_policy.not_found"));

        String extension = extensionOf(fileName);
        boolean listed = extension != null && policy.getExtensions().stream()
                .anyMatch(e -> e.equalsIgnoreCase(extension));

        boolean allowed = switch (policy.getMode()) {
            case ALLOWLIST -> listed;
            case DENYLIST -> !listed;
        };
        if (!allowed) {
            throw new IllegalArgumentException("file.validation.extension_not_allowed");
        }
        if (sizeBytes > policy.getMaxSizeBytes()) {
            throw new IllegalArgumentException("file.validation.size_exceeded");
        }
    }

    private static final Map<String, String> CONTENT_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("webp", "image/webp"),
            Map.entry("gif", "image/gif"),
            Map.entry("csv", "text/csv"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("doc", "application/msword"),
            Map.entry("txt", "text/plain"));

    /**
     * Content-Type to store/serve a file with, derived from its (already
     * validated) extension — never the client's declared type: a {@code .png}
     * uploaded as {@code text/html} would otherwise be served as a page from
     * the storage domain when staff open it. Unknown extensions fall back to
     * {@code application/octet-stream}, which browsers download instead of render.
     */
    public static String safeContentType(String fileName) {
        String extension = extensionOf(fileName);
        return extension == null ? "application/octet-stream"
                : CONTENT_TYPES.getOrDefault(extension, "application/octet-stream");
    }

    private static String extensionOf(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return null;
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
