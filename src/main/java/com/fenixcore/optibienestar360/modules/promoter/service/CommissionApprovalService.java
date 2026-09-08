package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionApprovalActionResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionApprovalGroupDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionApprovalRowDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.CommissionStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride.OverrideStatus;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Commercial approval gate between calculation and payout (V107, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §4, PR5): a
 * calculated {@link Commission} is a simulation ({@code PENDING}) until
 * gerencia comercial explicitly {@link #approveRows approves} or {@link
 * #rejectRows rejects} it, row by row — never bulk-by-promoter. Only the
 * direct commission carries this state; hierarchy overrides and retroactive
 * top-ups have no approval column of their own and inherit the fate of the
 * commission that funded them.
 *
 * <p><b>Rejection cascade</b>: voiding a commission also voids every {@code
 * promoter_hierarchy_override} that depends on it — directly ({@code
 * source_commission_id}) or transitively through a chain of overrides
 * ({@code source_override_id}) — replicating the cascade's own strict
 * one-level-at-a-time shape ({@code HierarchyOverrideService}). The walk
 * stops the moment it reaches an override that is no longer {@code
 * PENDING}: an already-{@code PAID} override is settled, out-of-band money
 * and is never reversed here (a real fraud/refund case needs a separate,
 * manual reconciliation — not a gap in this cascade), and anything already
 * {@code VOIDED} has nothing further to cascade from. Retroactive top-ups
 * aren't part of this walk — they aggregate a whole settlement period
 * rather than pointing at one commission, so they're simply recomputed
 * (and shrink accordingly) the next time {@code
 * CommissionRetroactiveTopUpService} runs.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CommissionApprovalService {

    private final CommissionRepository commissionRepository;
    private final PromoterHierarchyOverrideRepository overrideRepository;
    private final UserRepository userRepository;
    private final CommissionAuditRecorder auditRecorder;

    /**
     * Feeds the 2-level expandable table: level 1 is one row per promoter
     * with the period's total-to-commission (collapsed); level 2, per
     * promoter, is every individual commission in the period regardless of
     * status — {@code PENDING} ones preselected and editable, anything else
     * locked read-only so the gerente comercial sees the period's full
     * "todo" even for money they can no longer act on.
     */
    public List<CommissionApprovalGroupDto> approvalQueue(LocalDate periodStart, LocalDate periodEnd) {
        List<Commission> all = commissionRepository.findAllForPeriod(periodStart, periodEnd);

        Map<Promoter, List<Commission>> byPromoter = new LinkedHashMap<>();
        for (Commission c : all) {
            byPromoter.computeIfAbsent(c.getPromoter(), k -> new ArrayList<>()).add(c);
        }

        List<CommissionApprovalGroupDto> groups = new ArrayList<>(byPromoter.size());
        for (Map.Entry<Promoter, List<Commission>> entry : byPromoter.entrySet()) {
            Promoter promoter = entry.getKey();
            List<CommissionApprovalRowDto> rows = entry.getValue().stream().map(CommissionApprovalService::toRowDto).toList();
            BigDecimal total = rows.stream()
                    .filter(CommissionApprovalRowDto::checked)
                    .map(CommissionApprovalRowDto::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String currency = rows.get(0).currencyCode();
            groups.add(new CommissionApprovalGroupDto(
                    promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName(),
                    total, currency, rows));
        }
        return groups;
    }

    private static CommissionApprovalRowDto toRowDto(Commission c) {
        boolean locked = !CommissionStatus.PENDING.name().equals(c.getStatus());
        boolean excluded = CommissionStatus.REJECTED.name().equals(c.getStatus())
                || CommissionStatus.VOIDED.name().equals(c.getStatus());
        return new CommissionApprovalRowDto(
                c.getUuid(), c.getAppliesTo(), c.getAmount(),
                c.getCurrency() != null ? c.getCurrency().getCode() : null,
                c.getPeriodStart(), c.getPeriodEnd(), c.getEarnedAt(),
                c.getStatus(), locked, !excluded);
    }

    @Transactional
    public CommissionApprovalActionResponse approveRows(List<UUID> commissionUuids, UUID actorUserUuid) {
        List<Commission> rows = resolvePendingOrThrow(commissionUuids);
        User actor = resolveActor(actorUserUuid);
        Instant now = Instant.now();

        for (Commission c : rows) {
            Map<String, Object> before = auditRecorder.snapshot(c);
            c.setStatus(CommissionStatus.APPROVED.name());
            c.setApprovedBy(actor);
            c.setApprovedAt(now);
            auditRecorder.recordUpdate(c.getUuid(), before, auditRecorder.snapshot(c));
        }
        log.info("COMMISSION_APPROVE rows={} actor={}", commissionUuids.size(), actorUserUuid);
        return new CommissionApprovalActionResponse(commissionUuids, 0);
    }

    @Transactional
    public CommissionApprovalActionResponse rejectRows(List<UUID> commissionUuids, UUID actorUserUuid, String reason) {
        List<Commission> rows = resolvePendingOrThrow(commissionUuids);
        User actor = resolveActor(actorUserUuid);
        Instant now = Instant.now();

        int cascadedOverridesVoided = 0;
        for (Commission c : rows) {
            Map<String, Object> before = auditRecorder.snapshot(c);
            c.setStatus(CommissionStatus.REJECTED.name());
            c.setApprovedBy(actor);
            c.setApprovedAt(now);
            c.setRejectionReason(reason);
            auditRecorder.recordUpdate(c.getUuid(), before, auditRecorder.snapshot(c));

            cascadedOverridesVoided += voidDependentOverrides(c.getId(), reason, now);
        }
        log.info("COMMISSION_REJECT rows={} actor={} cascadedOverridesVoided={}",
                commissionUuids.size(), actorUserUuid, cascadedOverridesVoided);
        return new CommissionApprovalActionResponse(commissionUuids, cascadedOverridesVoided);
    }

    /**
     * Every {@code commissionUuids} must currently be {@code PENDING} — the
     * whole request is rejected (400, via {@link IllegalArgumentException})
     * if any one isn't, rather than silently skipping the ones that don't
     * qualify. An unknown uuid throws {@link NoSuchElementException} (404).
     */
    private List<Commission> resolvePendingOrThrow(List<UUID> commissionUuids) {
        List<Commission> rows = commissionRepository.findByUuidIn(commissionUuids);
        if (rows.size() != commissionUuids.size()) {
            throw new NoSuchElementException("commission.not_found");
        }
        for (Commission c : rows) {
            if (!CommissionStatus.PENDING.name().equals(c.getStatus())) {
                throw new IllegalArgumentException("commission.approval.not_pending");
            }
        }
        return rows;
    }

    private User resolveActor(UUID actorUserUuid) {
        return actorUserUuid == null ? null : userRepository.findByUuid(actorUserUuid).orElse(null);
    }

    /**
     * BFS, one level at a time — mirrors {@code HierarchyOverrideService
     * .cascadeOnce}'s own strict shape. Returns the count actually voided
     * (skips, without descending further, any override that's no longer
     * {@code PENDING} — see class Javadoc).
     */
    private int voidDependentOverrides(Long commissionId, String reason, Instant now) {
        int count = 0;
        Deque<PromoterHierarchyOverride> queue = new ArrayDeque<>(overrideRepository.findBySourceCommissionId(commissionId));
        while (!queue.isEmpty()) {
            PromoterHierarchyOverride override = queue.poll();
            if (!OverrideStatus.PENDING.name().equals(override.getStatus())) {
                continue;
            }
            override.setStatus(OverrideStatus.VOIDED.name());
            override.setVoidedAt(now);
            override.setVoidReason("Cascaded from rejected commission: " + reason);
            count++;
            queue.addAll(overrideRepository.findBySourceOverrideId(override.getId()));
        }
        return count;
    }
}
