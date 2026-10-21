package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance.Channel;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.time.Instant;
import java.util.UUID;

/**
 * One contract the caller accepted — {@code GET /v1/me/terms/acceptances},
 * newest first. {@code allyUuid} / {@code allyName} / {@code allyAgreementUuid}
 * are set for ALIADO acceptances linked to an agreement (V189), {@code null}
 * otherwise. {@code renderedContent} is the text exactly as accepted; for
 * acceptances recorded before V189 (no stored text) it is the version's
 * template rendered with current data and {@code contentReconstructed} is
 * {@code true}.
 */
public record TermsAcceptanceHistoryDto(
        UUID uuid,
        TermType termType,
        UUID termsVersionUuid,
        int versionNumber,
        String title,
        UUID allyUuid,
        String allyName,
        UUID allyAgreementUuid,
        @Display(Display.Kind.DATETIME) Instant acceptedAt,
        Channel channel,
        String renderedContent,
        boolean contentReconstructed
) {}
