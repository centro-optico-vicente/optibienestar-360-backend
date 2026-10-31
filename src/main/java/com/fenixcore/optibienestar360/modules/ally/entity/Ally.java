package com.fenixcore.optibienestar360.modules.ally.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.core.jpa.CitextJdbcType;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.Profession;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Ally of the network (hub ADR 0026 / 0031). Its {@link #getSegment() segment}
 * says what it is: {@code B2B} "Aliado Comercial" — an organization (clinic,
 * pharmacy, optical store, hardware store, funeral home…) — or {@code B2P}
 * "Aliado Profesional" — a professional (physician, psychologist, lawyer…).
 * Each place a professional practices at is its own ally (one agreement per
 * ally), all sharing the same {@link #getHolderPerson() holder person}.
 *
 * <p>The holder person ({@code person_id}, V211) is the professional for B2P
 * (required by {@code AlliesService}) and the representative who signs the
 * agreement for B2B. Users that operate the ally attach via {@link AllyUser}
 * (N:M pivot, V12).</p>
 *
 * <p>Public visibility controlled by {@link #isPublished()} +
 * {@link #getPublishedAt()} — orthogonal to the lifecycle of the services
 * it offers (each {@link AllyProduct} has its own {@code reviewStatus} and
 * {@code isPublished}).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "allies",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tax_document_type", "tax_document_number"})
)
@AttributeOverride(name = "id", column = @Column(name = "allies_id", nullable = false, updatable = false))
public class Ally extends BaseEntity {

    @Column(length = 200, nullable = false)
    private String name;

    /**
     * Sequential "Nº DE ALIADO" (V179) — assigned by the DB sequence
     * {@code allies_ally_number_seq} on INSERT and read back by Hibernate;
     * never written by the application.
     */
    @Generated
    @Column(name = "ally_number", nullable = false, insertable = false, updatable = false)
    private Integer allyNumber;

    /** Advisor (promoter) who captured the ally; its referral code prefixes the agreement file number (V179). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "captured_by_promoter_id")
    private Promoter capturedByPromoter;

    /** B2B / B2P (V211, hub ADR 0031); the ally's types must belong to it. */
    @Enumerated(EnumType.STRING)
    @Column(name = "segment", length = 3, nullable = false)
    private AllyType.Segment segment = AllyType.Segment.B2B;

    /** Holder person (V211): the professional (B2P) or the representative who signs (B2B). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Person holderPerson;

    // ─── B2P practice site (V211, Convenio Profesional §III) ───────────────

    /** Clinic the professional practices at, when that clinic is an ally too. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_ally_id")
    private Ally hostAlly;

    /** Clinic name when it is not an ally. */
    @Column(name = "host_name", length = 200)
    private String hostName;

    @Column(name = "office_number", length = 20)
    private String officeNumber;

    @Column(name = "appointment_phone", length = 30)
    private String appointmentPhone;

    @Column(name = "assistant_name", length = 150)
    private String assistantName;

    // ─── B2B commercial data (V211, Convenio Comercial §I–IV) ───────────────

    @Column(name = "commercial_zone", length = 100)
    private String commercialZone;

    /** Nombre comercial; {@link #getName() name} is the legal name (razón social). */
    @Column(name = "trade_name", length = 200)
    private String tradeName;

    @Column(name = "founded_year")
    private Short foundedYear;

    @Column(name = "cash_registers_count")
    private Integer cashRegistersCount;

    @Column(name = "branches_count")
    private Integer branchesCount;

    @Column(name = "has_pos")
    private Boolean hasPos;

    /** Position (cargo) of the B2B representative who signs. */
    @Column(name = "representative_position", length = 100)
    private String representativePosition;

    // ─── Legal representative / physician who signs the agreement (V179) ───
    // DEPRECATED since V211: the holder person (person_id) replaces these
    // free-text columns; kept read-only-ish until their data is migrated.

    @Column(name = "representative_name", length = 210)
    private String representativeName;

    @Column(name = "representative_document_type", length = 1)
    private String representativeDocumentType;

    @Column(name = "representative_document_number", length = 20)
    private String representativeDocumentNumber;

    // ─── Ally types (ally_ally_types pivot) — at least 1 required, ─────────
    // enforced in AlliesService, not at the DB level (M:N has no NOT NULL).

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ally_ally_types",
            joinColumns = @JoinColumn(name = "ally_id"),
            inverseJoinColumns = @JoinColumn(name = "ally_type_id")
    )
    private Set<AllyType> allyTypes = new HashSet<>();

    // ─── Tax identity (Venezuelan RIF) ──────────────────────────────────────

    @Column(name = "tax_document_type", length = 1)
    private String taxDocumentType;

    @Column(name = "tax_document_number", length = 20)
    private String taxDocumentNumber;

    // ─── Contact ────────────────────────────────────────────────────────────

    @Column(columnDefinition = "citext")
    @JdbcType(CitextJdbcType.class)
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String website;

    @Column(length = 30)
    private String whatsapp;

    @Column(length = 255)
    private String instagram;

    @Column(length = 255)
    private String facebook;

    // ─── Address ────────────────────────────────────────────────────────────

    @Column(columnDefinition = "text")
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    @Column(name = "google_maps_url", length = 500)
    private String googleMapsUrl;

    /** Promotion code the ally hands out (V175, hub ADR 0018); attribution only, stored upper-case. */
    @Column(name = "referral_code", length = 20)
    private String referralCode;

    // ─── Branding ───────────────────────────────────────────────────────────

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    // ─── Directory publishing ───────────────────────────────────────────────

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    // ─── Professions (ally_professions pivot) ────────────────────────────────

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ally_professions",
            joinColumns = @JoinColumn(name = "ally_id"),
            inverseJoinColumns = @JoinColumn(name = "profession_id")
    )
    private Set<Profession> professions = new HashSet<>();

    // ─── User memberships (V12 ally_users) ──────────────────────────────────

    @OneToMany(mappedBy = "ally", fetch = FetchType.LAZY)
    private List<AllyUser> users = new ArrayList<>();

    // ─── Products and services offered (ADR 0026) ───────────────────────────

    @OneToMany(mappedBy = "ally", fetch = FetchType.LAZY)
    private List<AllyProduct> products = new ArrayList<>();

    // ─── Agreements ─────────────────────────────────────────────────────────

    @OneToMany(mappedBy = "ally", fetch = FetchType.LAZY)
    private List<AllyAgreement> agreements = new ArrayList<>();

    // ─── Weekly opening hours (V214, hub ADR 0031 §7) ───────────────────────
    // Read-only from this side: AllyOpeningHoursService replaces the week.

    @OneToMany(mappedBy = "ally", fetch = FetchType.LAZY)
    @OrderBy("dayOfWeek ASC, opensAt ASC")
    private List<AllyOpeningHour> openingHours = new ArrayList<>();
}
