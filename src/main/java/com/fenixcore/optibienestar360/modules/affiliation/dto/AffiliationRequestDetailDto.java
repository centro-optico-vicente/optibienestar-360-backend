package com.fenixcore.optibienestar360.modules.affiliation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Full request for the admin review screen ({@code GET /v1/admin/affiliation-requests/{uuid}}).
 *
 * <p>{@code termsRenderedContent} is the contract exactly as the applicant
 * accepted it (markdown). {@code termsChanged} tells whether the data as it
 * stands now renders a different text — after a correction of a value the
 * contract shows (name, document, birth date, plan). Approving then creates
 * the member without the acceptance, and the member is asked to accept the
 * corrected text at first login. The password hash is never exposed.</p>
 *
 * <p>{@code channel} is {@code SELF_SERVICE} (public form) or {@code ASSISTED}
 * (filled by {@code advisor} from their portal; {@code createdBy} is that
 * advisor's user). {@code passwordPending} is {@code true} when no password
 * was chosen: approval emails a link to create it instead of the login link.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AffiliationRequestDetailDto(
        UUID uuid,

        String firstName,
        String lastName,
        String fullName,
        String documentType,
        String documentNumber,
        @Display(Display.Kind.DATE) LocalDate birthDate,
        String email,
        String phone,
        String locale,

        @Display DisplayRef plan,
        @Display DisplayRef advisor,
        String referralCode,
        @Display(value = Display.Kind.ENUM, enumScope = "affiliation_request.channel") String channel,
        @Display DisplayRef createdBy,
        boolean passwordPending,

        @Display DisplayRef termsVersion,
        String termsRenderedContent,
        String termsContentSha256,
        @Display(Display.Kind.DATETIME) Instant termsAcceptedAt,
        String ipAddress,
        String userAgent,
        boolean termsChanged,

        @Display(value = Display.Kind.ENUM, enumScope = "affiliation_request.status") String status,
        @Display DisplayRef reviewedBy,
        @Display(Display.Kind.DATETIME) Instant reviewedAt,
        String rejectionReason,

        @Display DisplayRef user,
        @Display DisplayRef member,
        @Display DisplayRef membership,

        @Display(Display.Kind.DATETIME) Instant createdAt,
        List<AffiliationRequestLogDto> log
) {}
