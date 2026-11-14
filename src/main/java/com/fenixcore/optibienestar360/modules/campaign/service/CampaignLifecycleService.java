package com.fenixcore.optibienestar360.modules.campaign.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignCloseRequest;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignReopenRequest;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.CloseReason;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Lifecycle transitions of a campaign (hub ADR 0038 §3): activate, pause,
 * resume, close and reopen by hand, plus the END_DATE close of the daily
 * {@code CAMPAIGN_LIFECYCLE} job. Every transition locks the campaign row
 * first so it can't race a slot reservation. An invalid transition answers
 * 422 ({@code campaign.lifecycle.invalid_transition}), the project's style for
 * a broken business rule.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class CampaignLifecycleService {

    private final CampaignRepository repository;
    private final UserRepository userRepository;

    /** DRAFT → ACTIVE (or SOLD_OUT when the quota is already full). */
    @Transactional
    public Campaign activate(UUID uuid) {
        Campaign campaign = lock(uuid);
        require(campaign, LifecycleStatus.DRAFT);
        campaign.setLifecycleStatus(LifecycleStatus.ACTIVE);
        campaign.recomputeQuotaStatus(OffsetDateTime.now());
        return campaign;
    }

    /** ACTIVE | SOLD_OUT → PAUSED: stops offering without closing. */
    @Transactional
    public Campaign pause(UUID uuid) {
        Campaign campaign = lock(uuid);
        require(campaign, LifecycleStatus.ACTIVE, LifecycleStatus.SOLD_OUT);
        campaign.setLifecycleStatus(LifecycleStatus.PAUSED);
        return campaign;
    }

    /** PAUSED → ACTIVE, or SOLD_OUT when the quota is full. */
    @Transactional
    public Campaign resume(UUID uuid) {
        Campaign campaign = lock(uuid);
        require(campaign, LifecycleStatus.PAUSED);
        campaign.setLifecycleStatus(LifecycleStatus.ACTIVE);
        campaign.recomputeQuotaStatus(OffsetDateTime.now());
        return campaign;
    }

    /** Any state but CLOSED → CLOSED by hand (reason MANUAL, who and when recorded). */
    @Transactional
    public Campaign close(UUID uuid, CampaignCloseRequest request, UUID actorUuid) {
        Campaign campaign = lock(uuid);
        if (campaign.getLifecycleStatus() == LifecycleStatus.CLOSED) {
            throw new IllegalArgumentException("campaign.lifecycle.invalid_transition");
        }
        User actor = actorUuid == null ? null : userRepository.findByUuid(actorUuid).orElse(null);
        String note = request != null && request.note() != null && !request.note().isBlank()
                ? request.note().trim() : null;
        campaign.close(CloseReason.MANUAL, actor, note, OffsetDateTime.now());
        return campaign;
    }

    /**
     * CLOSED → ACTIVE (or SOLD_OUT when still full). Only when the request
     * enlarges the campaign: a quota above the current one, or an end date
     * later than now and than the current end. Clears every {@code closed_*}
     * column.
     */
    @Transactional
    public Campaign reopen(UUID uuid, CampaignReopenRequest request) {
        Campaign campaign = lock(uuid);
        require(campaign, LifecycleStatus.CLOSED);
        OffsetDateTime now = OffsetDateTime.now();

        Integer newMax = request != null ? request.maxRedemptions() : null;
        OffsetDateTime newEnd = request != null ? request.endsAt() : null;
        boolean enlargesQuota = newMax != null && campaign.getMaxRedemptions() != null
                && newMax > campaign.getMaxRedemptions();
        boolean extendsEnd = newEnd != null && newEnd.isAfter(now) && newEnd.isAfter(campaign.getStartsAt())
                && (campaign.getEndsAt() == null || newEnd.isAfter(campaign.getEndsAt()));
        if ((newMax != null && !enlargesQuota) || (newEnd != null && !extendsEnd) || (!enlargesQuota && !extendsEnd)) {
            throw new IllegalArgumentException("campaign.reopen.not_enlarged");
        }
        if (enlargesQuota) {
            campaign.setMaxRedemptions(newMax);
        }
        if (extendsEnd) {
            campaign.setEndsAt(newEnd);
        }
        if (campaign.isCloseOnEndDate() && campaign.getEndsAt() != null && !campaign.getEndsAt().isAfter(now)) {
            throw new IllegalArgumentException("campaign.reopen.ends_at_passed");
        }
        if (campaign.isCloseOnQuotaFilled() && campaign.getMaxRedemptions() != null
                && campaign.getConfirmedCount() >= campaign.getMaxRedemptions()) {
            throw new IllegalArgumentException("campaign.reopen.not_enlarged");
        }
        campaign.clearClose();
        campaign.setLifecycleStatus(LifecycleStatus.ACTIVE);
        campaign.recomputeQuotaStatus(now);
        return campaign;
    }

    /**
     * END_DATE close of the daily job: every not-yet-closed campaign with the
     * END_DATE condition whose end already passed. Automatic, so no actor.
     *
     * @return how many campaigns were closed
     */
    @Transactional
    public int closeEnded(OffsetDateTime now) {
        int closed = 0;
        for (Campaign due : repository.findDueForEndDateClose(
                EnumSet.of(LifecycleStatus.ACTIVE, LifecycleStatus.SOLD_OUT, LifecycleStatus.PAUSED), now)) {
            Campaign campaign = repository.findForUpdateById(due.getId()).orElse(null);
            if (campaign == null || campaign.getLifecycleStatus() == LifecycleStatus.CLOSED) {
                continue;
            }
            campaign.close(CloseReason.END_DATE, null, null, campaign.getEndsAt());
            closed++;
            log.info("Campaign {} closed by END_DATE (ended {})", campaign.getUuid(), campaign.getEndsAt());
        }
        return closed;
    }

    private Campaign lock(UUID uuid) {
        return repository.findForUpdateByUuid(uuid)
                .filter(Campaign::isActive)
                .orElseThrow(() -> new NoSuchElementException("campaign.not_found"));
    }

    private static void require(Campaign campaign, LifecycleStatus... allowed) {
        for (LifecycleStatus status : allowed) {
            if (campaign.getLifecycleStatus() == status) {
                return;
            }
        }
        throw new IllegalArgumentException("campaign.lifecycle.invalid_transition");
    }
}
