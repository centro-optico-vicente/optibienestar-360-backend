package com.fenixcore.optibienestar360.modules.affiliation.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * An affiliation waiting for review (V190) — from the public form or, since
 * V194, filled by an advisor from their portal ({@link Channel#ASSISTED}). Holds the applicant's
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

    /**
     * Values of {@code affiliation_requests.channel} (V194 CHECK): the public
     * form ({@code SELF_SERVICE}) or the advisor portal, filled by the advisor
     * with the family ({@code ASSISTED}).
     */
    public enum Channel { SELF_SERVICE, ASSISTED }

    @Column(name = "channel", length = 20, nullable = false)
    private String channel = Channel.SELF_SERVICE.name();

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

    /**
     * Advisor of the request: the one of the referral code the applicant
     * arrived with (public form; {@code null} without one), or the advisor who
     * filled it in an assisted affiliation.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id")
    private Promoter promoter;

    @Column(name = "referral_code", length = 20)
    private String referralCode;

    /**
     * Never exposed in any DTO; copied verbatim to {@code users.password_hash}
     * on approval. {@code null} only in an assisted request where the family
     * chose no password (V194 CHECK): approval then emails a link to create it.
     */
    @Column(name = "password_hash", length = 255)
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

    // ─── Reported inscription payment (V203) ───────────────────────────────
    // All null when the applicant reported no payment; method and date are set
    // together (V203 CHECK). Amount/currency are the plan's inscription fee at
    // submit time, never the client's.

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_method_id")
    private PaymentMethod paymentMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_bank_id")
    private Bank paymentBank;

    @Column(name = "payment_amount", precision = 10, scale = 2)
    private BigDecimal paymentAmount;

    @Column(name = "payment_currency_code", length = 3)
    private String paymentCurrencyCode;

    @Column(name = "payment_identification", length = 80)
    private String paymentIdentification;

    @Column(name = "payment_bank_account_type", length = 40)
    private String paymentBankAccountType;

    @Column(name = "payment_bank_account_code", length = 40)
    private String paymentBankAccountCode;

    @Column(name = "payment_bank_account_identifier", length = 120)
    private String paymentBankAccountIdentifier;

    @Column(name = "payment_phone", length = 40)
    private String paymentPhone;

    @Column(name = "payment_email", length = 160)
    private String paymentEmail;

    @Column(name = "payment_reference_number", length = 80)
    private String paymentReferenceNumber;

    @Column(name = "payment_date")
    private Instant paymentDate;

    /** R2 object key of the proof; {@code null} when none was attached or storage was off at upload time. */
    @Column(name = "payment_support_file_url", length = 500)
    private String paymentSupportFileUrl;

    @Column(name = "payment_support_file_name", length = 255)
    private String paymentSupportFileName;

    @Column(name = "payment_support_file_content_type", length = 100)
    private String paymentSupportFileContentType;

    @Column(name = "payment_support_file_size_bytes")
    private Long paymentSupportFileSizeBytes;

    /** The inscription payment registered from this data on approval. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    public boolean hasReportedPayment() {
        return paymentMethod != null;
    }

    public boolean isAssisted() {
        return Channel.ASSISTED.name().equals(channel);
    }

    public boolean isPending() {
        return Status.PENDING.name().equals(getStatus());
    }

    /** {@code "first last"} with blanks collapsed — the same rule as the {@code persons.full_name} column. */
    public String getFullName() {
        String joined = (firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName);
        return joined.trim().replaceAll("\\s+", " ");
    }
}
