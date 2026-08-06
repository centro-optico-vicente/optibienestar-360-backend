package com.fenixcore.optibienestar360.modules.person.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.core.jpa.CitextJdbcType;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.Gender;
import com.fenixcore.optibienestar360.modules.catalog.entity.MaritalStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Civic / contact identity of a human in the system. One row per real person
 * regardless of how many roles they hold (User, Member titular, Beneficiary,
 * Promoter, AllyUser). See ADR 0012 — Persons as identity hub.
 *
 * <p>{@code fullName} is a Postgres GENERATED STORED column derived from the
 * four name parts — Hibernate maps it as {@code insertable=false updatable=false}
 * so the app never writes it directly. Set the parts (firstName / lastName / ...)
 * and the database recomputes the composed name.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "persons",
        uniqueConstraints = @UniqueConstraint(columnNames = {"document_type", "document_number"})
)
@AttributeOverride(name = "id", column = @Column(name = "persons_id", nullable = false, updatable = false))
public class Person extends BaseEntity {

    // ─── Civic identity ─────────────────────────────────────────────────────

    @Column(name = "first_name", length = 50, nullable = false)
    private String firstName;

    @Column(name = "middle_name", length = 50)
    private String middleName;

    @Column(name = "last_name", length = 50, nullable = false)
    private String lastName;

    @Column(name = "second_last_name", length = 50)
    private String secondLastName;

    /**
     * GENERATED STORED column in Postgres — readable only. The DB recomputes it
     * from the four name parts on each INSERT/UPDATE. Hibernate must not try
     * to write it.
     */
    @Column(name = "full_name", length = 210, insertable = false, updatable = false)
    private String fullName;

    // ─── Documents ──────────────────────────────────────────────────────────

    @Column(name = "document_type", length = 2, nullable = false)
    private String documentType;

    @Column(name = "document_number", length = 20, nullable = false)
    private String documentNumber;

    @Column(name = "tax_document_type", length = 1)
    private String taxDocumentType;

    @Column(name = "tax_document_number", length = 20)
    private String taxDocumentNumber;

    // ─── Demographics ───────────────────────────────────────────────────────

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gender_id")
    private Gender gender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "marital_status_id")
    private MaritalStatus maritalStatus;

    /** "Lugar de Nacimiento" on the inscription form — free text (city/state). */
    @Column(name = "birthplace", length = 120)
    private String birthplace;

    /** "Cantidad de Hijos" on the inscription form. Non-negative when present. */
    @Column(name = "number_of_children")
    private Integer numberOfChildren;

    /**
     * "Cónyuge" on the inscription form — the literal spouse-name line. If the
     * spouse is affiliated they also appear as a {@code Beneficiary} with
     * relationship SPOUSE; this field is the paper-faithful capture and is not
     * kept in sync with that row.
     */
    @Column(name = "spouse_name", length = 210)
    private String spouseName;

    // ─── Contact ────────────────────────────────────────────────────────────

    /** Canonical contact number — the "Celular" slot on the inscription form. */
    @Column(length = 30)
    private String phone;

    /** "Teléfono Fijo" on the inscription form — landline, distinct from {@link #phone}. */
    @Column(name = "landline_phone", length = 30)
    private String landlinePhone;

    @Column(columnDefinition = "citext")
    @JdbcType(CitextJdbcType.class)
    private String email;

    @Column(length = 10)
    private String locale;

    // ─── Address ────────────────────────────────────────────────────────────

    @Column(columnDefinition = "text")
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;
}
