package com.fenixcore.optibienestar360.modules.affiliation.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A public self-affiliation waiting for review (V190). Holds the applicant's
 * data, the BCrypt hash of the password they chose and the evidence of the
 * contract they accepted; nothing else exists until a reviewer approves it
 * ({@code AffiliationRequestService.approve} creates the user, the member and
 * the membership from this row and links them back here).
 *
 * <p>The workflow status lives in {@link BaseEntity#getStatus()} as one of
 * {@link Status}; the V190 CHECK keeps any other value out.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "affiliation_requests")
@AttributeOverride(name = "id", column = @Column(name = "affiliation_requests_id", nullable = false, updatable = false))
public class AffiliationRequest extends BaseEntity {

    /** Values of {@code affiliation_requests.status} (V190 CHECK). */
    public enum Status { PENDING, APPROVED, REJECTED }

    @Column(name = "first_name", length = 50, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 50, nullable = false)
    private String lastName;

    @Column(name = "document_type", length = 2, nullable = false)
    private String documentType;

    @Column(name = "document_number", length = 20, nullable = false)
    private String documentNumber;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    /** Stored lower-cased; the partial unique index compares {@code LOWER(email)}. */
    @Column(name = "email", length = 254, nullable = false)
    private String email;

    @Column(name = "phone", length = 30)
    private String phone;

    /** Language of the form ({@code es} / {@code en}) — the request emails go out in it. */
    @Column(name = "locale", length = 10)
    private String locale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    /** Advisor of the referral code the applicant arrived with; {@code null} without one. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id")
    private Promoter promoter;

    @Column(name = "referral_code", length = 20)
    private String referralCode;

    /** Never exposed in any DTO; copied verbatim to {@code users.password_hash} on approval. */
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    // ─── Contract evidence ──────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terms_versions_id", nullable = false)
    private TermsVersion termsVersion;

    @Column(name = "terms_rendered_content", columnDefinition = "text", nullable = false)
    private String termsRenderedContent;

    @Column(name = "terms_content_sha256", length = 64, nullable = false)
    private String termsContentSha256;

    @Column(name = "terms_accepted_at", nullable = false)
    private Instant termsAcceptedAt;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    // ─── Review ─────────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "rejection_reason", columnDefinition = "text")
    private String rejectionReason;

    // ─── Created on approval ────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private Membership membership;

    public boolean isPending() {
        return Status.PENDING.name().equals(getStatus());
    }

    /** {@code "first last"} with blanks collapsed — the same rule as the {@code persons.full_name} column. */
    public String getFullName() {
        String joined = (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
        return joined.trim().replaceAll("\\s+", " ");
    }
}
