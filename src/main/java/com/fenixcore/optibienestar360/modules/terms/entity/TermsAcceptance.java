package com.fenixcore.optibienestar360.modules.terms.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Records that {@link #user} accepted {@link #termsVersion} at
 * {@link #acceptedAt}. Unique per {@code (user, version)} for AFILIADO /
 * ASESOR and per {@code (user, version, agreement)} for ALIADO — two partial
 * unique indexes (V189), so one ALIADO template shared by two of the user's
 * allies is accepted once per agreement, each with its own data.
 *
 * <p>Accepted text (V189): {@link #renderedContent} is the contract exactly as
 * shown (placeholders resolved) and {@link #contentSha256} its hex SHA-256, so
 * the record survives later edits of the organization, the member or the
 * ally. Both are {@code null} on acceptances recorded before V189.</p>
 *
 * <p>Evidence columns (V181): {@link #ipAddress}, {@link #userAgent} and
 * {@link #channel} record how the acceptance was given; {@link #membership}
 * links it to the membership it was given for (self-service affiliation).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "terms_acceptances")
@AttributeOverride(name = "id", column = @Column(name = "terms_acceptances_id", nullable = false, updatable = false))
public class TermsAcceptance extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "terms_versions_id", nullable = false)
    private TermsVersion termsVersion;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt = Instant.now();

    /** Where the acceptance happened — mirrors {@code chk_terms_acceptances_channel}. */
    public enum Channel { PORTAL, SELF_SERVICE, ASSISTED, ADMIN }

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel = Channel.PORTAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "membership_id")
    private Membership membership;

    /** The ally agreement an ALIADO acceptance was given for; {@code null} for the other kinds and unlinked legacy rows. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ally_agreement_id")
    private AllyAgreement allyAgreement;

    @Column(name = "rendered_content", columnDefinition = "TEXT")
    private String renderedContent;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;
}
