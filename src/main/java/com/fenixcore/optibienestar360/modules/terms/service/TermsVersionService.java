package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.repository.AllyTypeRepository;
import com.fenixcore.optibienestar360.modules.terms.dto.PublicTermsDto;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionCreateRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionDto;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionUpdateRequest;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Admin CRUD over {@link TermsVersion} — vigency-by-timestamp, same pattern
 * as {@code ExchangeRateService}. Rows are never overwritten once vigente
 * (see {@link #update}); "editing" a live T&C means publishing a new version.
 *
 * <p>ALIADO templates carry an optional scope (V183); each scope combination
 * is its own stream with its own current/scheduled version. The "current"
 * lookups by type ({@link #getCurrent}, the public endpoint) resolve the
 * unscoped stream — the only one for AFILIADO / ASESOR, the general
 * template for ALIADO.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsVersionService {

    private final TermsVersionRepository repository;
    private final TermsTemplateRenderer renderer;
    private final AllyTypeRepository allyTypeRepository;
    private final AllyRepository allyRepository;

    public List<TermsVersionDto> listByType(TermType termType) {
        return repository.findByTermTypeOrderByValidFromDesc(termType).stream().map(this::toDto).toList();
    }

    public List<TermsVersionDto> listAll() {
        return repository.findAllByOrderByValidFromDesc().stream().map(this::toDto).toList();
    }

    public TermsVersionDto get(UUID uuid) {
        return toDto(findManaged(uuid));
    }

    public TermsVersionDto getCurrent(TermType termType) {
        return toDto(currentOrThrow(termType));
    }

    /**
     * {@code GET /v1/admin/allies/{allyUuid}/applicable-terms} — the current
     * ALIADO templates the ally may sign (general ones plus those whose scope
     * matches the ally, its types or their segments), most specific first.
     */
    public List<TermsVersionDto> listApplicableToAlly(UUID allyUuid) {
        Ally ally = allyRepository.findByUuid(allyUuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
        return TermsVersionScopes.currentApplicableTo(
                        repository.findByTermTypeOrderByValidFromDesc(TermType.ALLY), ally, Instant.now())
                .stream().map(this::toDto).toList();
    }

    /**
     * Anonymous-facing lookup — the current version for {@code termType}, but
     * only if it is marked public. 404s otherwise (never leaks that a
     * non-public version exists).
     */
    public TermsVersion getCurrentPublic(TermType termType) {
        TermsVersion current = currentOrThrow(termType);
        if (!current.isPublic()) {
            throw new NoSuchElementException("terms_version.not_found");
        }
        return current;
    }

    /** {@link #getCurrentPublic} projected for anonymous callers, placeholders resolved. */
    public PublicTermsDto getCurrentPublicRendered(TermType termType) {
        TermsVersion version = getCurrentPublic(termType);
        return new PublicTermsDto(
                version.getUuid(),
                version.getTermType(),
                version.getTitle(),
                renderer.render(version.getContentMarkdown()),
                version.getValidFrom());
    }

    /**
     * The version a public self-affiliation must have accepted: {@code uuid}
     * has to be the current, public version of {@code termType} — a stale or
     * arbitrary UUID (e.g. the form was open while a new version went live)
     * is rejected so the applicant re-reads the vigente text.
     */
    public TermsVersion requireCurrentPublic(TermType termType, UUID uuid) {
        return currentUnscoped(termType)
                .filter(TermsVersion::isPublic)
                .filter(current -> current.getUuid().equals(uuid))
                .orElseThrow(() -> new IllegalArgumentException("affiliation.terms.not_current"));
    }

    @Transactional
    @Auditable(entity = "terms_version", action = AuditAction.CREATE)
    public TermsVersionDto create(TermsVersionCreateRequest req) {
        TermsVersion version = new TermsVersion();
        version.setTermType(req.termType());
        version.setTitle(req.title());
        version.setContentMarkdown(req.contentMarkdown());
        version.setPublic(req.isPublic() != null && req.isPublic());
        version.setValidFrom(req.validFrom());
        applyScope(version, req.allyTypeUuid(), req.segment(), req.allyUuid());

        if (req.validFrom().isAfter(Instant.now())) {
            rejectIfStreamAlreadyScheduled(version);
        }

        return toDto(repository.save(version));
    }

    /**
     * Only permitted while {@code validFrom} is still in the future — once a
     * version has gone live it is immutable (audit/legal integrity: what a
     * user accepted must never silently change under them).
     */
    @Transactional
    @Auditable(entity = "terms_version", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public TermsVersionDto update(UUID uuid, TermsVersionUpdateRequest req) {
        TermsVersion version = findManaged(uuid);
        if (!version.getValidFrom().isAfter(Instant.now())) {
            throw new IllegalArgumentException("terms_version.immutable");
        }

        if (req.title() != null) version.setTitle(req.title());
        if (req.contentMarkdown() != null) version.setContentMarkdown(req.contentMarkdown());
        if (req.isPublic() != null) version.setPublic(req.isPublic());
        if (req.validFrom() != null) {
            if (!req.validFrom().isAfter(Instant.now())) {
                throw new IllegalArgumentException("terms_version.valid_from_must_be_future");
            }
            version.setValidFrom(req.validFrom());
        }
        if (req.allyTypeUuid() != null || req.segment() != null || req.allyUuid() != null) {
            applyScope(version,
                    req.allyTypeUuid() != null ? req.allyTypeUuid() : uuidOf(version.getAllyType()),
                    req.segment() != null ? req.segment() : version.getSegment(),
                    req.allyUuid() != null ? req.allyUuid() : uuidOf(version.getAlly()));
            // Moving a scheduled version to another stream must not leave two scheduled there.
            rejectIfStreamAlreadyScheduled(version);
        }

        return toDto(version);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private TermsVersion findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("terms_version.not_found"));
    }

    private TermsVersion currentOrThrow(TermType termType) {
        return currentUnscoped(termType)
                .orElseThrow(() -> new NoSuchElementException("terms_version.not_found"));
    }

    private Optional<TermsVersion> currentUnscoped(TermType termType) {
        return repository
                .findFirstByTermTypeAndAllyTypeIsNullAndSegmentIsNullAndAllyIsNullAndValidFromLessThanEqualOrderByValidFromDesc(
                        termType, Instant.now());
    }

    /**
     * Sets the ALIADO scope of {@code version} (V183). Any scope field on a
     * non-ALIADO version is a 422; an ally type whose segment contradicts the
     * chosen segment would match no ally, so it is rejected too.
     */
    private void applyScope(TermsVersion version, UUID allyTypeUuid, AllyType.Segment segment, UUID allyUuid) {
        boolean scoped = allyTypeUuid != null || segment != null || allyUuid != null;
        if (scoped && version.getTermType() != TermType.ALLY) {
            throw new IllegalArgumentException("terms_version.scope.only_aliado");
        }
        AllyType allyType = allyTypeUuid == null ? null : allyTypeRepository.findByUuid(allyTypeUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_type.not_found"));
        Ally ally = allyUuid == null ? null : allyRepository.findByUuid(allyUuid)
                .orElseThrow(() -> new NoSuchElementException("ally.not_found"));
        if (allyType != null && segment != null && allyType.getSegment() != segment) {
            throw new IllegalArgumentException("terms_version.scope.segment_mismatch");
        }
        version.setAllyType(allyType);
        version.setSegment(segment);
        version.setAlly(ally);
    }

    /** Only one not-yet-vigente version per stream (type + scope); {@code version} itself is ignored. */
    private void rejectIfStreamAlreadyScheduled(TermsVersion version) {
        boolean taken = repository.findByTermTypeAndValidFromGreaterThan(version.getTermType(), Instant.now()).stream()
                .filter(other -> version.getId() == null || !other.getId().equals(version.getId()))
                .anyMatch(other -> other.sameStreamAs(version));
        if (taken) {
            throw new IllegalArgumentException("terms_version.already_scheduled");
        }
    }

    private static UUID uuidOf(AllyType allyType) {
        return allyType == null ? null : allyType.getUuid();
    }

    private static UUID uuidOf(Ally ally) {
        return ally == null ? null : ally.getUuid();
    }

    // Resolves the organization once per row; the admin list is a handful of versions.
    private TermsVersionDto toDto(TermsVersion v) {
        AllyType allyType = v.getAllyType();
        Ally ally = v.getAlly();
        return new TermsVersionDto(
                v.getUuid(),
                v.getTermType(),
                v.getTitle(),
                v.getContentMarkdown(),
                renderer.render(v.getContentMarkdown()),
                v.isPublic(),
                v.getValidFrom(),
                !v.getValidFrom().isAfter(Instant.now()),
                allyType == null ? null : DisplayRef.of(allyType.getUuid(), allyType.getCode(), allyType.getName()),
                v.getSegment(),
                ally == null ? null : DisplayRef.of(ally.getUuid(), null, ally.getName()),
                v.isActive(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }
}
