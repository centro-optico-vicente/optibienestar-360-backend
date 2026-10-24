package com.fenixcore.optibienestar360.modules.terms.dto;

import java.util.UUID;

/**
 * Optional body of {@code POST /v1/me/terms/{termsVersionUuid}/accept}:
 * {@code allyAgreementUuid} (from {@code GET /v1/me/terms/pending}) is only
 * needed for an ALIADO version, which may be pending for several agreements.
 */
public record TermsAcceptOneRequest(UUID allyAgreementUuid) {
}
