package com.fenixcore.optibienestar360.common.storage;

import java.util.UUID;

/**
 * Builds R2 object keys as {@code "{visibility}/{ownerTable}/{ownerUuid}/{fileName}"}
 * — spec {@code .ai/specs/08-storage-r2.md} §2. Shared by every attachment
 * consumer (payments, member/ally documents, catalog images, future
 * reporting) so the layout stays uniform.
 */
public final class StorageKeyBuilder {

    private StorageKeyBuilder() {
    }

    public static String build(FileVisibility visibility, String ownerTable, UUID ownerUuid, String fileName) {
        return visibility.keySegment() + "/" + ownerTable + "/" + ownerUuid + "/" + UUID.randomUUID()
                + "-" + safeName(fileName);
    }

    /** Strips anything but alphanumerics/dot/dash/underscore — same rule PaymentsService already applied. */
    public static String safeName(String original) {
        if (original == null || original.isBlank()) return "file";
        return original.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
