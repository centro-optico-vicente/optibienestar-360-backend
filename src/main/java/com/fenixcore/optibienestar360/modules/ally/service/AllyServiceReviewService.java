package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyServiceReviewLogDto;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyServiceReviewLog;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceReviewLogRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The ally-service approval workflow (v2 PDF #6) — the state machine + its
 * immutable audit log. Owns the transitions over {@link AllyService}'s
 * {@code reviewStatus}:
 * <pre>
 *   PROPOSED / IN_REVIEW → APPROVED   (approve)
 *   PROPOSED / IN_REVIEW → REJECTED   (reject, reason required)
 *   APPROVED             → REMOVED    (remove, reason required — admin OR ally owner)
 * </pre>
 *
 * <p>Every transition writes one {@link AllyServiceReviewLog} row so the history
 * is auditable, and the negative transitions stamp {@code reviewedBy} +
 * {@code reviewReason} to satisfy the V11 CHECK. Admin actions are gated by the
 * {@code ALLY_SERVICE_APPROVE} permission at the controller; the ally-owner
 * removal is gated here by an active OWNER/STAFF membership on the parent ally.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AllyServiceReviewService {

    /** User-facing default when the ally removes their own service without a reason. */
    private static final String DEFAULT_ALLY_REMOVAL_REASON = "Retirado por el aliado";

    /** Free-text {@code ?q=} fields for the pending queue — the proposed service's own name/description. */
    private static final String[] SEARCHABLE_FIELDS = {"name", "description"};

    private final AllyServiceRepository serviceRepository;
    private final AllyServiceReviewLogRepository logRepository;
    private final AllyUserRepository allyUserRepository;
    private final UserRepository userRepository;
    private final AllyMapper mapper;
    private final AllyServiceImageService imageService;

    @Value("${storage.r2.public-base-url:}")
    private String publicBaseUrl;

    // ─── Admin review queue ───────────────────────────────────────────────────

    /**
     * The review queue: services awaiting a decision (PROPOSED or IN_REVIEW),
     * optionally filtered by ally and/or service category.
     */
    public Page<AllyServiceDto> pendingQueue(UUID allyUuid, UUID serviceCategoryUuid, String q, Pageable pageable) {
        Specification<AllyService> spec = (root, query, cb) -> cb.and(
                cb.isTrue(root.get("active")),
                root.get("reviewStatus").in(ReviewStatus.PROPOSED, ReviewStatus.IN_REVIEW));

        if (allyUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("ally").get("uuid"), allyUuid));
        }
        if (serviceCategoryUuid != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("serviceCategory").get("uuid"), serviceCategoryUuid));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return serviceRepository.findAll(spec, pageable).map(service -> mapper.toServiceDto(service, publicBaseUrl));
    }

    // ─── Admin transitions ────────────────────────────────────────────────────

    @Transactional
    public AllyServiceDto approve(UUID serviceUuid, UUID actorUuid, String comment) {
        AllyService service = findService(serviceUuid);
        requireStatus(service, "ally_service.approve.invalid_state",
                ReviewStatus.PROPOSED, ReviewStatus.IN_REVIEW);

        ReviewStatus from = service.getReviewStatus();
        User actor = resolveActor(actorUuid);
        service.setReviewStatus(ReviewStatus.APPROVED);
        service.setReviewedBy(actor);
        service.setReviewedAt(Instant.now());
        service.setReviewReason(null);   // clear any prior rejection reason
        log(service, from, ReviewStatus.APPROVED, actor, comment);
        return mapper.toServiceDto(service, publicBaseUrl);
    }

    @Transactional
    public AllyServiceDto reject(UUID serviceUuid, UUID actorUuid, String reason) {
        AllyService service = findService(serviceUuid);
        requireStatus(service, "ally_service.reject.invalid_state",
                ReviewStatus.PROPOSED, ReviewStatus.IN_REVIEW);

        ReviewStatus from = service.getReviewStatus();
        User actor = resolveActor(actorUuid);
        applyNegativeTransition(service, ReviewStatus.REJECTED, actor, reason);
        log(service, from, ReviewStatus.REJECTED, actor, reason);
        return mapper.toServiceDto(service, publicBaseUrl);
    }

    /** Admin pulls an approved service out of the directory. */
    @Transactional
    public AllyServiceDto adminRemove(UUID serviceUuid, UUID actorUuid, String reason) {
        AllyService service = findService(serviceUuid);
        User actor = resolveActor(actorUuid);
        return remove(service, actor, reason);
    }

    // ─── Ally-owner transition ────────────────────────────────────────────────

    /**
     * The ally owner retires their own APPROVED service. Membership-gated
     * (active OWNER/STAFF on the parent ally) rather than permission-gated — the
     * ally never holds {@code ALLY_SERVICE_APPROVE}. A blank reason falls back to
     * a default so the V11 CHECK (REMOVED requires a reason) always holds.
     */
    @Transactional
    public AllyServiceDto allyRemove(UUID serviceUuid, UUID actorUuid, String reason) {
        AllyService service = findService(serviceUuid);
        AllyUser membership = requireOwnerOrStaff(service, actorUuid);
        String finalReason = (reason == null || reason.isBlank())
                ? DEFAULT_ALLY_REMOVAL_REASON
                : reason.trim();
        return remove(service, membership.getUser(), finalReason);
    }

    // ─── Shared review log (admin sees any, ally sees only their own) ──────────

    public List<AllyServiceReviewLogDto> adminLog(UUID serviceUuid) {
        AllyService service = findService(serviceUuid);
        return mapLog(service);
    }

    public List<AllyServiceReviewLogDto> allyLog(UUID serviceUuid, UUID actorUuid) {
        AllyService service = findService(serviceUuid);
        // Any active membership (incl. VIEWER) may read the history; a caller with
        // no membership gets the same 404 as a non-existent service (anti-enumeration).
        allyUserRepository.findActiveByAllyUuidAndUserUuid(service.getAlly().getUuid(), actorUuid)
                .orElseThrow(() -> new NoSuchElementException("ally_service.not_found"));
        return mapLog(service);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private AllyServiceDto remove(AllyService service, User actor, String reason) {
        requireStatus(service, "ally_service.remove.invalid_state", ReviewStatus.APPROVED);
        ReviewStatus from = service.getReviewStatus();
        applyNegativeTransition(service, ReviewStatus.REMOVED, actor, reason);
        // A REMOVED service can't stay published (V11 CHECK is_published → APPROVED).
        service.setPublished(false);
        // Nor keep a publicly-reachable image — spec §5.
        imageService.unpublishIfPresent(service.getUuid());
        log(service, from, ReviewStatus.REMOVED, actor, reason);
        return mapper.toServiceDto(service, publicBaseUrl);
    }

    private static void applyNegativeTransition(AllyService service, ReviewStatus to,
                                                User actor, String reason) {
        service.setReviewStatus(to);
        service.setReviewedBy(actor);
        service.setReviewedAt(Instant.now());
        service.setReviewReason(reason);
    }

    private AllyUser requireOwnerOrStaff(AllyService service, UUID actorUuid) {
        AllyUser membership = allyUserRepository
                .findActiveByAllyUuidAndUserUuid(service.getAlly().getUuid(), actorUuid)
                .orElseThrow(() -> new AccessDeniedException("ally_service.remove.not_allowed"));
        if (membership.getAllyRole() == AllyRole.VIEWER) {
            throw new AccessDeniedException("ally_service.remove.not_allowed");
        }
        return membership;
    }

    private static void requireStatus(AllyService service, String errorCode, ReviewStatus... allowed) {
        for (ReviewStatus s : allowed) {
            if (service.getReviewStatus() == s) {
                return;
            }
        }
        throw new IllegalArgumentException(errorCode);
    }

    private void log(AllyService service, ReviewStatus from, ReviewStatus to, User actor, String comment) {
        AllyServiceReviewLog entry = new AllyServiceReviewLog();
        entry.setAllyService(service);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setActor(actor);
        entry.setActionAt(Instant.now());
        entry.setComment(comment);
        logRepository.save(entry);
    }

    private List<AllyServiceReviewLogDto> mapLog(AllyService service) {
        return logRepository.findByAllyServiceIdOrderByActionAtDesc(service.getId())
                .stream().map(AllyServiceReviewLogDto::from).toList();
    }

    private AllyService findService(UUID uuid) {
        return serviceRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("ally_service.not_found"));
    }

    private User resolveActor(UUID actorUuid) {
        return userRepository.findByUuid(actorUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
    }
}
