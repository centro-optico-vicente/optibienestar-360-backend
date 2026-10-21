package com.fenixcore.optibienestar360.modules.affiliation.dto;

import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /v1/admin/affiliation-requests/{uuid}/reject}. The
 * reason is mandatory and is emailed to the applicant; a missing or blank one
 * is a 422 ({@code affiliation_request.reject.reason_required}) raised by the
 * service, not a bean-validation 400, so the reject modal shows the localized
 * business message.
 */
public record AffiliationRequestRejectRequest(
        @Size(max = 2000) String reason
) {}
