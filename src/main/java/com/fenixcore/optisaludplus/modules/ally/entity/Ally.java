package com.fenixcore.optisaludplus.modules.ally.entity;

import com.fenixcore.optisaludplus.core.entity.BaseEntity;
import com.fenixcore.optisaludplus.modules.catalog.entity.AllyType;
import com.fenixcore.optisaludplus.modules.catalog.entity.City;
import com.fenixcore.optisaludplus.modules.catalog.entity.MedicalSpecialty;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * Aliado comercial / médico — entidad organizacional (clínica, farmacia,
 * óptica, ferretería, veterinaria, …). NO referencia {@code persons}: un
 * aliado es una organización, no un humano. Los usuarios que lo operan se
 * enganchan vía {@link AllyUser} (pivote N:M, V12).
 *
 * <p>Visibilidad pública controlada por {@link #isPublished()} +
 * {@link #getPublishedAt()} — ortogonal al ciclo de vida de los servicios
 * que ofrece (cada {@link AllyService} tiene su propio
 * {@code reviewStatus} y {@code isPublished}).</p>
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ally_type_id", nullable = false)
    private AllyType allyType;

    // ─── Identidad fiscal (RIF VE) ──────────────────────────────────────────

    @Column(name = "tax_document_type", length = 1)
    private String taxDocumentType;

    @Column(name = "tax_document_number", length = 20)
    private String taxDocumentNumber;

    // ─── Contacto ──────────────────────────────────────────────────────────

    @Column(columnDefinition = "citext")
    private String email;

    @Column(length = 30)
    private String phone;

    @Column(length = 255)
    private String website;

    // ─── Dirección ─────────────────────────────────────────────────────────

    @Column(columnDefinition = "text")
    private String address;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "city_id")
    private City city;

    // ─── Branding ──────────────────────────────────────────────────────────

    @Column(name = "logo_url", length = 500)
    private String logoUrl;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    // ─── Publicación en directorio ─────────────────────────────────────────

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "published_at")
    private Instant publishedAt;

    // ─── Especialidades médicas (pivote ally_specialties) ──────────────────

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "ally_specialties",
            joinColumns = @JoinColumn(name = "ally_id"),
            inverseJoinColumns = @JoinColumn(name = "medical_specialty_id")
    )
    private Set<MedicalSpecialty> specialties = new HashSet<>();

    // ─── Membresías de usuarios (V12 ally_users) ───────────────────────────

    @OneToMany(mappedBy = "ally", fetch = FetchType.LAZY)
    private List<AllyUser> users = new ArrayList<>();

    // ─── Servicios ofrecidos ───────────────────────────────────────────────

    @OneToMany(mappedBy = "ally", fetch = FetchType.LAZY)
    private List<AllyService> services = new ArrayList<>();

    // ─── Convenios / acuerdos ──────────────────────────────────────────────

    @OneToMany(mappedBy = "ally", fetch = FetchType.LAZY)
    private List<AllyAgreement> agreements = new ArrayList<>();
}
