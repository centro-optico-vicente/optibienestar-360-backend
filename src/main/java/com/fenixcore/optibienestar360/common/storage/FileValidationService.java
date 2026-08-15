package com.fenixcore.optibienestar360.common.storage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
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

    private static String extensionOf(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return null;
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
