package com.fenixcore.optisaludplus.modules.person.entity;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import com.fenixcore.optisaludplus.modules.catalog.entity.City;
import com.fenixcore.optisaludplus.modules.catalog.entity.Gender;
import com.fenixcore.optisaludplus.modules.catalog.entity.MaritalStatus;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

    // ─── Contact ────────────────────────────────────────────────────────────

    @Column(length = 30)
    private String phone;

    @Column(columnDefinition = "citext")
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
