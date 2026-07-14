package com.fenixcore.optibienestar360.modules.membership.service;

import com.fenixcore.optibienestar360.modules.membership.dto.MembershipCancelRequest;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipDto;
import com.fenixcore.optibienestar360.modules.membership.dto.MembershipReactivateRequest;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.membership.mapper.MembershipMapper;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle transitions for {@link Membership} — cancel + reactivate. Kept
 * separate from {@link MembershipsService} (which handles enrollment +
 * history) because the daily {@code MembershipStatusService} job will live
 * alongside these transitions and share the same set of state-machine
 * guards.
 *
 * <p>Transition rules (mirror the V21 CHECK constraint set):</p>
 * <ul>
 *   <li><b>cancel</b> — any non-{@code CANCELED} status may move to
 *       {@code CANCELED}. Sets {@code is_active = false} so the V21 partial
 *       UNIQUE {@code (member_id) WHERE is_active = TRUE} releases the slot
 *       and the member can be re-enrolled into another plan.</li>
 *   <li><b>reactivate</b> — only {@code SUSPENDED} and {@code EXPIRED} can
 *       move back to {@code ACTIVE}. {@code CANCELED} is terminal:
 *       reactivating a cancellation is conceptually a new enrollment, not
 *       a status flip — admin uses
 *       {@code POST /v1/admin/members/{uuid}/memberships} instead.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MembershipLifecycleService {

    private static final Set<String> REACTIVATABLE_STATUSES = Set.of(
            LifecycleStatus.SUSPENDED.name(),
            LifecycleStatus.EXPIRED.name()
    );

    private final MembershipRepository repository;
    private final MembershipMapper mapper;
    private final com.fenixcore.optibienestar360.modules.validator.service.ValidatorCacheService validatorCacheService;

    @Transactional
    public MembershipDto cancel(UUID uuid, MembershipCancelRequest request) {
        Membership membership = findManaged(uuid);

        if (LifecycleStatus.CANCELED.name().equals(membership.getStatus())) {
            throw new IllegalArgumentException("membership.already_canceled");
        }

        membership.setStatus(LifecycleStatus.CANCELED.name());
        // Soft-delete frees the partial UNIQUE so the member can be enrolled
        // into a different plan immediately — V21 design intent.
        membership.setActive(false);
        membership.setLastStatusChangeAt(Instant.now());
        membership.setLastStatusChangeReason(reasonOr(
                "Cancelled by admin",
                request != null ? request.reason() : null));

        validatorCacheService.evictForMembership(membership);
        return mapper.toDto(membership);
    }

    @Transactional
    public MembershipDto reactivate(UUID uuid, MembershipReactivateRequest request) {
        Membership membership = findManaged(uuid);

        if (!REACTIVATABLE_STATUSES.contains(membership.getStatus())) {
            throw new IllegalArgumentException("membership.not_reactivatable");
        }

        membership.setStatus(LifecycleStatus.ACTIVE.name());
        membership.setLastStatusChangeAt(Instant.now());
        membership.setLastStatusChangeReason(reasonOr(
                "Reactivated by admin",
                request != null ? request.reason() : null));

        validatorCacheService.evictForMembership(membership);
        return mapper.toDto(membership);
    }

    private Membership findManaged(UUID uuid) {
        return repository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("membership.not_found"));
    }

    private static String reasonOr(String defaultPrefix, String userReason) {
        if (userReason == null || userReason.isBlank()) {
            return defaultPrefix;
        }
        return defaultPrefix + ": " + userReason.strip();
    }
}
