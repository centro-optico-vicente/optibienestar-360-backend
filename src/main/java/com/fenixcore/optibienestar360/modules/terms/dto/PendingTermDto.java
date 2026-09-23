package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.util.UUID;

/**
 * One T&C the caller has not yet accepted — {@code GET /v1/me/terms/pending}.
 * Carries {@code contentMarkdown} directly (unlike {@code TermsVersionDto}'s
 * admin shape) so {@code TermsAcceptanceModal} can render it without a
 * follow-up call.
 */
public record PendingTermDto(
        UUID termsVersionUuid,
        TermType termType,
        String title,
        String contentMarkdown
) {}
