package com.fenixcore.optisaludplus.modules.ally.service;

import com.fenixcore.optisaludplus.core.util.RsqlFieldValidator;
import com.fenixcore.optisaludplus.core.util.SearchSpecifications;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyCreateRequest;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyListItemDto;
import com.fenixcore.optisaludplus.modules.ally.dto.AllyUpdateRequest;
import com.fenixcore.optisaludplus.modules.ally.entity.Ally;
import com.fenixcore.optisaludplus.modules.ally.mapper.AllyMapper;
import com.fenixcore.optisaludplus.modules.ally.repository.AllyRepository;
import com.fenixcore.optisaludplus.modules.catalog.entity.AllyType;
import com.fenixcore.optisaludplus.modules.catalog.entity.City;
import com.fenixcore.optisaludplus.modules.catalog.entity.MedicalSpecialty;
import com.fenixcore.optisaludplus.modules.catalog.repository.AllyTypeRepository;
import com.fenixcore.optisaludplus.modules.catalog.repository.CityRepository;
import com.fenixcore.optisaludplus.modules.catalog.repository.MedicalSpecialtyRepository;
import io.github.perplexhub.rsql.RSQLJPASupport;
import jakarta.persistence.criteria.Join;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Application service for the {@link Ally} entity (the organizational
 * directory). Named {@code AlliesService} (plural) to avoid a class-name
 * collision with {@link com.fenixcore.optisaludplus.modules.ally.entity.AllyService}
 * (the offerings entity).
 *
 * <p>Read methods return DTOs; mutations return the updated detail DTO.
 * FK lookups (allyType, city, specialties) resolve via the catalog repos and
 * fail with {@link NoSuchElementException} carrying a localizable message
 * code when the requested UUID doesn't exist — turned into 422 by
 * {@code GlobalExceptionHandler}.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AlliesService {

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "name", "email", "phone", "website",
            "taxDocumentType", "taxDocumentNumber",
            "published", "publishedAt", "joinedAt",
            "createdAt", "updatedAt", "active", "status"
    );

    private static final String[] SEARCHABLE_FIELDS = {"name", "email", "phone"};

    private final AllyRepository repository;
    private final AllyTypeRepository allyTypeRepository;
    private final CityRepository cityRepository;
    private final MedicalSpecialtyRepository medicalSpecialtyRepository;
    private final AllyMapper mapper;

    // ─── Finders ────────────────────────────────────────────────────────────

    public AllyDetailDto getDetail(UUID uuid) {
        return mapper.toDetail(findManaged(uuid));
    }

    /** Lookup by tax document (RIF). Both parts required; blank inputs → empty. */
    public Optional<Ally> findByDocument(String taxDocumentType, String taxDocumentNumber) {
        if (taxDocumentType == null || taxDocumentNumber == null
                || taxDocumentType.isBlank() || taxDocumentNumber.isBlank()) {
            return Optional.empty();
        }
        return repository.findByTaxDocumentTypeAndTaxDocumentNumber(
                taxDocumentType.trim(), taxDocumentNumber.trim());
    }

    /**
     * Admin list — paginated, RSQL-filterable, free-text searchable. Returns
     * the compact list-item projection (no nested collections, cheap to
     * render N rows).
     */
    public Page<AllyListItemDto> list(Pageable pageable, String filter, String q) {
        Specification<Ally> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS, "ally.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return repository.findAll(spec, pageable).map(mapper::toListItem);
    }

    /**
     * Public-directory query. Filters {@code is_active AND is_published}.
     * Both UUID filters optional — null means "no restriction".
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
                    query.distinct(true);
                }
                Join<Object, Object> specialties = root.join("specialties");
                return cb.equal(specialties.get("uuid"), medicalSpecialtyUuid);
            });
        }
        return repository.findAll(spec, pageable);
    }

    // ─── Mutations ──────────────────────────────────────────────────────────

    @Transactional
    public AllyDetailDto create(AllyCreateRequest req) {
        // Pre-check for duplicate RIF so the client gets 422
        // ally.tax_document.duplicate instead of the generic 409 from the
        // partial UNIQUE index in V11.
        if (req.taxDocumentType() != null && req.taxDocumentNumber() != null
                && repository.existsByTaxDocumentTypeAndTaxDocumentNumber(
                        req.taxDocumentType(), req.taxDocumentNumber())) {
            throw new IllegalArgumentException("ally.tax_document.duplicate");
        }

        Ally ally = new Ally();
        ally.setName(req.name());
        ally.setAllyType(resolveAllyType(req.allyTypeUuid()));
        ally.setTaxDocumentType(req.taxDocumentType());
        ally.setTaxDocumentNumber(req.taxDocumentNumber());
        ally.setEmail(req.email());
        ally.setPhone(req.phone());
        ally.setWebsite(req.website());
        ally.setAddress(req.address());
        ally.setCity(resolveCityOptional(req.cityUuid()));
        ally.setLogoUrl(req.logoUrl());
        ally.setDescription(req.description());
        ally.setJoinedAt(req.joinedAt());
        if (req.published() != null) ally.setPublished(req.published());
        ally.setPublishedAt(req.publishedAt());
        ally.setSpecialties(resolveSpecialties(req.specialtyUuids()));

        Ally saved = repository.save(ally);
        return mapper.toDetail(saved);
    }

    @Transactional
    public AllyDetailDto update(UUID uuid, AllyUpdateRequest req) {
        Ally ally = findManaged(uuid);

        // Same uniqueness pre-check as create, but excluding self.
        if (req.taxDocumentType() != null && req.taxDocumentNumber() != null) {
            repository.findByTaxDocumentTypeAndTaxDocumentNumber(
                            req.taxDocumentType(), req.taxDocumentNumber())
                    .filter(other -> !other.getId().equals(ally.getId()))
                    .ifPresent(other -> {
                        throw new IllegalArgumentException("ally.tax_document.duplicate");
                    });
        }

        if (req.name()                != null) ally.setName(req.name());
        if (req.allyTypeUuid()        != null) ally.setAllyType(resolveAllyType(req.allyTypeUuid()));
        if (req.taxDocumentType()     != null) ally.setTaxDocumentType(req.taxDocumentType());
        if (req.taxDocumentNumber()   != null) ally.setTaxDocumentNumber(req.taxDocumentNumber());
        if (req.email()               != null) ally.setEmail(req.email());
        if (req.phone()               != null) ally.setPhone(req.phone());
        if (req.website()             != null) ally.setWebsite(req.website());
        if (req.address()             != null) ally.setAddress(req.address());
        if (req.cityUuid()            != null) ally.setCity(resolveCityOptional(req.cityUuid()));
        if (req.logoUrl()             != null) ally.setLogoUrl(req.logoUrl());
        if (req.description()         != null) ally.setDescription(req.description());
        if (req.joinedAt()            != null) ally.setJoinedAt(req.joinedAt());
        if (req.published()           != null) ally.setPublished(req.published());
        if (req.publishedAt()         != null) ally.setPublishedAt(req.publishedAt());
        if (req.active()              != null) ally.setActive(req.active());
        if (req.status()              != null) ally.setStatus(req.status());

        // specialtyUuids: null = leave untouched; non-null = replace.
        // Reuse the existing collection instance (clear + addAll) instead of
        // replacing the reference so Hibernate's orphan tracking on the
        // @ManyToMany sees one managed collection rather than a swap.
        if (req.specialtyUuids() != null) {
            Set<MedicalSpecialty> resolved = resolveSpecialties(req.specialtyUuids());
            ally.getSpecialties().clear();
            ally.getSpecialties().addAll(resolved);
        }

        return mapper.toDetail(ally);  // managed → dirty-check on commit
    }

    @Transactional
    public void delete(UUID uuid) {
        Ally ally = findManaged(uuid);
        ally.setActive(false);
        // Auto-unpublish on soft-delete: a deactivated ally should NOT keep
        // appearing in the public directory just because is_published is true.
        ally.setPublished(false);
    }

    // ─── Specialties sub-resource (single-item add / remove) ────────────────

    /**
     * List the medical specialties currently attached to the ally. Mirrors
     * the {@code specialties} field of {@link AllyDetailDto} but exposed as
     * a dedicated sub-resource for the {@code /v1/admin/allies/{uuid}/specialties}
     * endpoint family.
     */
    public List<com.fenixcore.optisaludplus.modules.catalog.dto.MedicalSpecialtyDto>
            listSpecialties(UUID allyUuid) {
        Ally ally = findManaged(allyUuid);
        return mapper.toMedicalSpecialtyDtoList(ally.getSpecialties());
    }

    /**
     * Attach a single medical specialty to the ally. Idempotent — re-adding an
     * already-present specialty is a no-op and still returns 200.
     */
    @Transactional
    public void addSpecialty(UUID allyUuid, UUID specialtyUuid) {
        Ally ally = findManaged(allyUuid);
        MedicalSpecialty specialty = medicalSpecialtyRepository.findByUuid(specialtyUuid)
                .orElseThrow(() -> new NoSuchElementException("medical_specialty.not_found"));
        ally.getSpecialties().add(specialty);  // Set semantics → idempotent
    }

    /**
     * Detach a single medical specialty from the ally. Idempotent — removing
     * a non-attached specialty is a no-op.
     */
    @Transactional
    public void removeSpecialty(UUID allyUuid, UUID specialtyUuid) {
        Ally ally = findManaged(allyUuid);
        ally.getSpecialties().removeIf(ms -> ms.getUuid().equals(specialtyUuid));
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Ally findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
    }

    private AllyType resolveAllyType(UUID allyTypeUuid) {
        return allyTypeRepository.findByUuid(allyTypeUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_type.not_found"));
    }

    private City resolveCityOptional(UUID cityUuid) {
        if (cityUuid == null) return null;
        return cityRepository.findByUuid(cityUuid)
                .orElseThrow(() -> new NoSuchElementException("city.not_found"));
    }

    private Set<MedicalSpecialty> resolveSpecialties(List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return new HashSet<>();
        }
        // LinkedHashSet preserves request order — friendlier for debugging
        // and irrelevant to the underlying @ManyToMany semantics.
        Set<MedicalSpecialty> resolved = new LinkedHashSet<>();
        for (UUID uuid : uuids) {
            resolved.add(medicalSpecialtyRepository.findByUuid(uuid)
                    .orElseThrow(() -> new NoSuchElementException("medical_specialty.not_found")));
        }
        return resolved;
    }

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
