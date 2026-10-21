package com.fenixcore.optibienestar360.modules.affiliation.dto;

import jakarta.validation.constraints.Size;

/** Optional body of {@code POST /v1/admin/affiliation-requests/{uuid}/approve}: a note for the log. */
public record AffiliationRequestApproveRequest(
        @Size(max = 2000) String comment
) {}
