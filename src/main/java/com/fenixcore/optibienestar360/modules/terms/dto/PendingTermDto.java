package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;

import java.time.Instant;
import java.util.UUID;

/**
 * One T&C the caller has not yet accepted — {@code GET /v1/me/terms/pending}.
 * Carries {@code contentMarkdown} directly (unlike {@code TermsVersionDto}'s
 * admin shape) so {@code TermsAcceptanceModal} can render it without a
 * follow-up call. {@code contentMarkdown} is already rendered (placeholders
 * resolved, see {@code TermsTemplateRenderer}).
 *
 * <p>ALIADO items are one per ally agreement (V189): the same template shared
 * by two of the user's allies is two items, each rendered with its own
 * agreement's data. {@code allyUuid}, {@code allyName} and
 * {@code allyAgreementUuid} identify that agreement ({@code null} for
 * AFILIADO / ASESOR); {@code allyAgreementUuid} goes back in
 * {@code POST /v1/me/terms/accept}'s {@code items}.</p>
 *
 * <p>{@code versionNumber} is the 1-based position of the version in its
 * stream (same type and ALIADO scope, by {@code validFrom}).
 * {@code previousAcceptance} is the text the user accepted last for the same
 * agreement (or, failing that, the same ally; for AFILIADO / ASESOR, the same
 * type) so the client can show what changed; {@code null} on a first
 * acceptance.</p>
 *
 * <p>{@code mandatory} is the version's {@code is_mandatory} (V192, editable
 * by the admin; AFILIADO by default): a mandatory item blocks the whole app.
 * An optional one can be postponed; until accepted, the self-service endpoints of its kind
 * ({@code /v1/advisors/me/**}, {@code /v1/allies/me/**}) answer 403
 * {@code terms.pending} ({@code TermsGateInterceptor}).</p>
 */
public record PendingTermDto(
        UUID termsVersionUuid,
        TermType termType,
        String title,
        String contentMarkdown,
        int versionNumber,
        UUID allyUuid,
        String allyName,
        UUID allyAgreementUuid,
        PreviousAcceptance previousAcceptance,
        boolean mandatory
) {

    /**
     * The last acceptance the pending item replaces. {@code renderedContent} is
     * the stored accepted text; for acceptances recorded before V189 (no stored
     * text) it is that older version's template rendered with current data.
     */
    public record PreviousAcceptance(
            UUID termsVersionUuid,
            int versionNumber,
            @Display(Display.Kind.DATETIME) Instant acceptedAt,
            String renderedContent
    ) {}
}
