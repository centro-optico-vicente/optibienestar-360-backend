package com.fenixcore.optibienestar360.common.storage.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Response for {@code listWithChecklist} — spec §4.1: required categories vs. what's actually uploaded. */
public record ChecklistDto(List<Item> documents, boolean complete) {

    public record Item(String category, Status status, UUID fileUuid, String fileName, Instant uploadedAt) {
        public static Item missing(String category) {
            return new Item(category, Status.MISSING, null, null, null);
        }

        public static Item uploaded(String category, UUID fileUuid, String fileName, Instant uploadedAt) {
            return new Item(category, Status.UPLOADED, fileUuid, fileName, uploadedAt);
        }
    }

    public enum Status {
        UPLOADED, MISSING
    }
}
