package com.fenixcore.optibienestar360.modules.corporate.dto;

import com.fenixcore.optibienestar360.core.display.Display;

import java.util.List;
import java.util.UUID;

/**
 * Result of a bulk enrollment ({@code POST /v1/admin/corporate-contracts/{uuid}/members}).
 * Reports the batch totals plus a per-row outcome so the admin sees exactly
 * which people were enrolled and which were skipped as already-enrolled
 * duplicates — the batch itself never fails on a duplicate (only on a
 * structural error, which rolls everything back).
 *
 * @param requested how many entries the request carried
 * @param enrolled  how many new members were created
 * @param skipped   how many were skipped as already enrolled
 * @param results   per-entry outcome, in request order
 */
public record CorporateBulkEnrollResponse(
        int requested,
        int enrolled,
        int skipped,
        List<Entry> results
) {
    /**
     * One row's outcome. {@code memberUuid} is the created member on ENROLLED,
     * {@code null} on SKIPPED_DUPLICATE. Document + name echo the input so the
     * admin can line the result up against their source list.
     */
    public record Entry(
            String documentType,
            String documentNumber,
            String fullName,
            @Display(Display.Kind.ENUM) Outcome outcome,
            UUID memberUuid
    ) {}

    public enum Outcome {
        ENROLLED,
        SKIPPED_DUPLICATE
    }
}
