package com.fenixcore.optisaludplus.modules.member.entity;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import com.fenixcore.optisaludplus.modules.catalog.entity.Occupation;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.promoter.entity.Promoter;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Program subscriber (titular de afiliación). 1:1 with {@link Person} —
 * demographic data lives in the identity hub and is never duplicated here.
 *
 * <p>Every Member is by definition a titular; family members covered under
 * the plan are modeled as {@link Beneficiary} rows pointing at this entity
 * and at their own Person. No {@code is_holder} flag exists because the
 * type distinction is structural, not a field.</p>
 *
 * <p>{@link #occupation} is a snapshot taken at affiliation time — if the
 * person changes jobs after enrolling, this stays at the original value
 * (used for marketing segmentation and any future occupation-based
 * pricing). Updating it requires an explicit admin action.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "members",
       uniqueConstraints = @UniqueConstraint(columnNames = "person_id"))
@AttributeOverride(name = "id", column = @Column(name = "members_id", nullable = false, updatable = false))
public class Member extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private Person person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "occupation_id")
    private Occupation occupation;

    /**
     * Employment snapshot from the inscription form, taken at affiliation time
     * (like {@link #occupation}, not tracked if the titular changes jobs).
     * "Lugar de Trabajo" — the employer / workplace name.
     */
    @Column(name = "employer_name", length = 150)
    private String employerName;

    /** "Cargo" on the inscription form — job title, distinct from the occupation catalog. */
    @Column(name = "job_position", length = 100)
    private String jobPosition;

    /** "Dirección Empresa" on the inscription form — the employer's address. */
    @Column(name = "employer_address", columnDefinition = "text")
    private String employerAddress;

    @Column(name = "enrolled_at", nullable = false)
    private LocalDate enrolledAt = LocalDate.now();

    @Column(columnDefinition = "text")
    private String notes;

    /**
     * Permanent attribution link to the sales-network promoter (V25).
     * Schema-nullable to let back-fill scripts land, but the enrollment
     * service enforces NOT NULL — every new member resolves to either a
     * real promoter (by {@code referral_code} on the request) or the
     * INSTITUCION fallback. Reassigning this link is admin-only via
     * {@code POST /v1/admin/members/{uuid}/assign-promoter}
     * (future bullet, v2 PDF 2.a "permanent link").
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "promoter_id")
    private Promoter promoter;

    /**
     * Member's own short code (V27) for the affiliate-to-affiliate
     * referral program. The service generates a 6-8 char UPPER
     * alphanumeric tag at enrollment with collision retry. NULL only
     * for back-fill rows.
     */
    @Column(name = "referral_code", length = 20)
    private String referralCode;

    // ─── Reverse sides ──────────────────────────────────────────────────────

    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
    private List<Beneficiary> beneficiaries = new ArrayList<>();

    @OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
    private List<MemberDocument> documents = new ArrayList<>();
}
