package com.fenixcore.optisaludplus.modules.ally.service;

import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.ally.entity.Ally;
import com.fenixcore.optisaludplus.modules.ally.repository.AllyRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.criteria.Join;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for the {@link Ally} entity (the organizational
 * directory). Named {@code AlliesService} (plural) to avoid a class-name
 * collision with {@link com.fenixcore.optisaludplus.modules.ally.entity.AllyService}
 * (the offerings entity); the workflow / offerings service lives separately.
 *
 * <p>Read-only by default. Mutation methods (create/update/delete, publish,
 * agreement and user management) will land with the CRUD / endpoint bullets;
 * this commit only delivers the search-and-find surface called out by the
 * checklist ({@code findByDocument}, {@code searchByLocationAndSpecialty})
 * plus the pagination scaffolding shared with the catalog services.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlliesService {

    /**
     * RSQL whitelist — only these field names may appear on the left side of
     * a comparison in the {@code ?filter=} query param. Any other name is
     * rejected with 422 to prevent attackers from traversing the entity graph
     * (e.g. {@code manager.passwordHash=='...'}).
     */
    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "email", "phone", "website",
            "taxDocumentType", "taxDocumentNumber",
            "published", "publishedAt", "joinedAt",
            "createdAt", "updatedAt", "active", "status"
    );

    /**
     * Fields searched by the free-text {@code ?q=} param, OR'd together with
     * {@code unaccent(lower(...)) LIKE} via {@link SearchSpecifications}.
     */
    private static final String[] SEARCHABLE_FIELDS = {"name", "email", "phone"};

    private final AllyRepository repository;

    // ─── Single-entity finders ──────────────────────────────────────────────

    public Ally getByUuid(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
    }

    public Optional<Ally> findByUuid(UUID uuid) {
        return repository.findByUuid(uuid);
    }

    /**
     * Lookup by tax document (RIF). Both parts are required — the V11 schema
     * stores them as a pair (UNIQUE partial index when both present).
     * Returns empty if no ally has that RIF.
     */
    public Optional<Ally> findByDocument(String taxDocumentType, String taxDocumentNumber) {
        if (taxDocumentType == null || taxDocumentNumber == null
                || taxDocumentType.isBlank() || taxDocumentNumber.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTaxDocumentTypeAndTaxDocumentNumber(
                taxDocumentType.trim(), taxDocumentNumber.trim());
    }

    // ─── Listings ───────────────────────────────────────────────────────────

    /**
     * Admin list (no published filter). Same pagination / RSQL / {@code q}
     * scaffolding as the catalog services — see ADR on pagination conventions.
     */
    public Page<Ally> list(Pageable pageable, String filter, String q) {
        Specification<Ally> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "ally.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable);
    }

    /**
     * Public directory listing — only {@code is_active AND is_published} rows.
     * Optional filters by city UUID and / or medical specialty UUID.
     *
     * <p>Both filters are nullable. With both null this returns every
     * published ally (paginated). The specialty filter JOINs through the
     * {@code ally_specialties} pivot using the entity's {@code @ManyToMany}
     * relation; {@code distinct=true} avoids duplicates when an ally has
     * multiple specialties.</p>
     */
    public Page<Ally> searchByLocationAndSpecialty(UUID cityUuid,
                                                   UUID medicalSpecialtyUuid,
                                                   Pageable pageable) {
        Specification<Ally> spec = publishedOnly();

        if (cityUuid != null) {
            spec = spec.and((root, query, cb) ->
                    cb.equal(root.get("city").get("uuid"), cityUuid));
        }

        if (medicalSpecialtyUuid != null) {
            spec = spec.and((root, query, cb) -> {
                if (query != null) {
                    query.distinct(true);  // avoid duplicates from the join
                }
                Join<Object, Object> specialties = root.join("specialties");
                return cb.equal(specialties.get("uuid"), medicalSpecialtyUuid);
            });
        }

        return repository.findAll(spec, pageable);
    }

    // ─── Specification helpers ──────────────────────────────────────────────

    private static Specification<Ally> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }

    private static Specification<Ally> publishedOnly() {
        return (root, query, cb) -> cb.and(
                cb.isTrue(root.get("active")),
                cb.isTrue(root.get("published"))
        );
    }
}
