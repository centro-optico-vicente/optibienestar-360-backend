package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
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
import java.util.UUID;

/**
 * Admin CRUD over {@link TermsVersion} — vigency-by-timestamp, same pattern
 * as {@code ExchangeRateService}. Rows are never overwritten once vigente
 * (see {@link #update}); "editing" a live T&C means publishing a new version.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TermsVersionService {

    private final TermsVersionRepository repository;
    private final TermsTemplateRenderer renderer;

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
        return repository.findFirstByTermTypeAndValidFromLessThanEqualOrderByValidFromDesc(termType, Instant.now())
                .filter(TermsVersion::isPublic)
                .filter(current -> current.getUuid().equals(uuid))
                .orElseThrow(() -> new IllegalArgumentException("affiliation.terms.not_current"));
    }

    @Transactional
    @Auditable(entity = "terms_version", action = AuditAction.CREATE)
    public TermsVersionDto create(TermsVersionCreateRequest req) {
        Instant now = Instant.now();
        if (req.validFrom().isAfter(now)) {
            repository.findFirstByTermTypeAndValidFromGreaterThanOrderByValidFromAsc(req.termType(), now)
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("terms_version.already_scheduled");
                    });
        }

        TermsVersion version = new TermsVersion();
        version.setTermType(req.termType());
        version.setTitle(req.title());
        version.setContentMarkdown(req.contentMarkdown());
        version.setPublic(req.isPublic() != null && req.isPublic());
        version.setValidFrom(req.validFrom());

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

        return toDto(version);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private TermsVersion findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("terms_version.not_found"));
    }

    private TermsVersion currentOrThrow(TermType termType) {
        return repository.findFirstByTermTypeAndValidFromLessThanEqualOrderByValidFromDesc(termType, Instant.now())
                .orElseThrow(() -> new NoSuchElementException("terms_version.not_found"));
    }

    // Resolves the organization once per row; the admin list is a handful of versions.
    private TermsVersionDto toDto(TermsVersion v) {
        return new TermsVersionDto(
                v.getUuid(),
                v.getTermType(),
                v.getTitle(),
                v.getContentMarkdown(),
                renderer.render(v.getContentMarkdown()),
                v.isPublic(),
                v.getValidFrom(),
                !v.getValidFrom().isAfter(Instant.now()),
                v.isActive(),
                v.getCreatedAt(),
                v.getUpdatedAt()
        );
    }
}
