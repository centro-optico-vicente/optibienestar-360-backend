package com.fenixcore.optibienestar360.modules.campaign.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignCloseRequest;
import com.fenixcore.optibienestar360.modules.campaign.dto.CampaignReopenRequest;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.CloseReason;
import com.fenixcore.optibienestar360.modules.campaign.entity.Campaign.LifecycleStatus;
import com.fenixcore.optibienestar360.modules.campaign.repository.CampaignRepository;
import com.fenixcore.optibienestar360.modules.promotion.service.PromotionRedemptionService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Campaign lifecycle transitions and closing conditions (hub ADR 0038 §2-§3). */
@ExtendWith(MockitoExtension.class)
class CampaignLifecycleServiceTest {

    @Mock private CampaignRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private PromotionRedemptionService redemptionService;
    @Mock private EntityManager entityManager;

    private CampaignLifecycleService service() {
        return new CampaignLifecycleService(repository, userRepository, redemptionService, entityManager);
    }

    private Campaign campaign(LifecycleStatus status) {
        Campaign c = new Campaign();
        c.setId(7L);
        c.setUuid(UUID.randomUUID());
        c.setName("Navidad");
        c.setStartsAt(OffsetDateTime.now().minusDays(10));
        c.setEndsAt(OffsetDateTime.now().plusDays(10));
        c.setLifecycleStatus(status);
        if (status == LifecycleStatus.CLOSED) {
            c.close(CloseReason.MANUAL, null, null, OffsetDateTime.now().minusDays(1));
        }
        lenient().when(repository.findForUpdateByUuid(c.getUuid())).thenReturn(Optional.of(c));
        return c;
    }

    // ─── Transitions ────────────────────────────────────────────────────────

    @Test
    void activate_movesADraftToActive_andUsesTheRowLock() {
        Campaign c = campaign(LifecycleStatus.DRAFT);

        assertThat(service().activate(c.getUuid()).getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        verify(repository).findForUpdateByUuid(c.getUuid());
        // Re-read under the lock: a copy loaded earlier in the request must not write stale counters back.
        verify(entityManager).refresh(c);
    }

    @Test
    void pauseAndResume_roundTrip_resumeLandsInSoldOutWhenFull() {
        Campaign c = campaign(LifecycleStatus.ACTIVE);
        c.setMaxRedemptions(3);
        c.setRedemptionsCount(3);
        c.setConfirmedCount(1);

        assertThat(service().pause(c.getUuid()).getLifecycleStatus()).isEqualTo(LifecycleStatus.PAUSED);
        assertThat(service().resume(c.getUuid()).getLifecycleStatus()).isEqualTo(LifecycleStatus.SOLD_OUT);

        c.setRedemptionsCount(2);
        service().pause(c.getUuid());
        assertThat(service().resume(c.getUuid()).getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
    }

    @Test
    void invalidTransitions_are422() {
        Campaign draft = campaign(LifecycleStatus.DRAFT);
        assertThatThrownBy(() -> service().pause(draft.getUuid()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("campaign.lifecycle.invalid_transition");
        assertThatThrownBy(() -> service().resume(draft.getUuid()))
                .hasMessage("campaign.lifecycle.invalid_transition");

        Campaign closed = campaign(LifecycleStatus.CLOSED);
        assertThatThrownBy(() -> service().close(closed.getUuid(), new CampaignCloseRequest(false, null), null))
                .hasMessage("campaign.lifecycle.invalid_transition");
        assertThatThrownBy(() -> service().activate(closed.getUuid()))
                .hasMessage("campaign.lifecycle.invalid_transition");
    }

    @Test
    void manualClose_recordsReasonWhoAndWhen() {
        Campaign c = campaign(LifecycleStatus.ACTIVE);
        User admin = new User();
        UUID adminUuid = UUID.randomUUID();
        when(userRepository.findByUuid(adminUuid)).thenReturn(Optional.of(admin));

        Campaign closed = service().close(c.getUuid(), new CampaignCloseRequest(false, "  Fin anticipado "), adminUuid);

        assertThat(closed.getLifecycleStatus()).isEqualTo(LifecycleStatus.CLOSED);
        assertThat(closed.getClosedReason()).isEqualTo(CloseReason.MANUAL);
        assertThat(closed.getClosedBy()).isSameAs(admin);
        assertThat(closed.getClosedNote()).isEqualTo("Fin anticipado");
        assertThat(closed.getClosedAt()).isNotNull();
        verify(redemptionService, org.mockito.Mockito.never()).cancelPending(any());
        // Open-ended usage windows and vouchers end with the close.
        verify(redemptionService).onCampaignClosed(c);
    }

    @Test
    void manualClose_withCancelPending_releasesThePendingSlots() {
        Campaign c = campaign(LifecycleStatus.ACTIVE);

        service().close(c.getUuid(), new CampaignCloseRequest(true, null), null);

        verify(redemptionService).cancelPending(c);
    }

    @Test
    void manualClose_isTheOnlyWayOut_whenNoConditionIsConfigured() {
        Campaign c = campaign(LifecycleStatus.ACTIVE);
        c.setEndsAt(null);
        c.setCloseOnEndDate(false);
        c.setCloseOnQuotaFilled(false);
        c.setMaxRedemptions(1);
        c.setRedemptionsCount(1);
        c.setConfirmedCount(1);

        c.recomputeQuotaStatus(OffsetDateTime.now());
        assertThat(c.getLifecycleStatus()).isEqualTo(LifecycleStatus.SOLD_OUT);
        assertThat(c.effectiveEndsAt()).isNull();

        Campaign closed = service().close(c.getUuid(), new CampaignCloseRequest(false, null), null);
        assertThat(closed.getClosedReason()).isEqualTo(CloseReason.MANUAL);
        assertThat(closed.effectiveEndsAt()).isEqualTo(closed.getClosedAt());
    }

    // ─── Reopen ─────────────────────────────────────────────────────────────

    @Test
    void reopen_withoutEnlarging_is422() {
        Campaign c = campaign(LifecycleStatus.CLOSED);
        c.setMaxRedemptions(10);

        assertThatThrownBy(() -> service().reopen(c.getUuid(), new CampaignReopenRequest(null, null)))
                .hasMessage("campaign.reopen.not_enlarged");
        assertThatThrownBy(() -> service().reopen(c.getUuid(), new CampaignReopenRequest(10, null)))
                .hasMessage("campaign.reopen.not_enlarged");
        assertThatThrownBy(() -> service().reopen(c.getUuid(), new CampaignReopenRequest(null, c.getEndsAt().minusDays(1))))
                .hasMessage("campaign.reopen.not_enlarged");
        assertThat(c.getLifecycleStatus()).isEqualTo(LifecycleStatus.CLOSED);
    }

    @Test
    void reopen_raisingTheQuota_clearsTheClose() {
        Campaign c = campaign(LifecycleStatus.ACTIVE);
        c.setMaxRedemptions(2);
        c.setCloseOnQuotaFilled(true);
        c.setRedemptionsCount(2);
        c.setConfirmedCount(2);
        c.recomputeQuotaStatus(OffsetDateTime.now());
        assertThat(c.getClosedReason()).isEqualTo(CloseReason.QUOTA_FILLED);

        Campaign reopened = service().reopen(c.getUuid(), new CampaignReopenRequest(5, null));

        assertThat(reopened.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(reopened.getMaxRedemptions()).isEqualTo(5);
        assertThat(reopened.getClosedAt()).isNull();
        assertThat(reopened.getClosedReason()).isNull();
        assertThat(reopened.getClosedBy()).isNull();
    }

    @Test
    void reopen_movingTheEndDate_landsInSoldOutWhenStillFull() {
        Campaign c = campaign(LifecycleStatus.CLOSED);
        c.setMaxRedemptions(2);
        c.setRedemptionsCount(2);
        c.setConfirmedCount(1);

        Campaign reopened = service().reopen(c.getUuid(),
                new CampaignReopenRequest(null, c.getEndsAt().plusDays(30)));

        assertThat(reopened.getLifecycleStatus()).isEqualTo(LifecycleStatus.SOLD_OUT);
        assertThat(reopened.getClosedAt()).isNull();
    }

    @Test
    void reopen_ofAManualOnlyCampaign_withoutQuotaNorEndDate_needsNothingToEnlarge() {
        Campaign c = campaign(LifecycleStatus.CLOSED);
        c.setEndsAt(null);
        c.setCloseOnEndDate(false);
        c.setRedemptionsCount(7);

        Campaign reopened = service().reopen(c.getUuid(), new CampaignReopenRequest(null, null));

        assertThat(reopened.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
        assertThat(reopened.getMaxRedemptions()).isNull();
        assertThat(reopened.getClosedAt()).isNull();
    }

    @Test
    void reopen_settingAFirstQuota_aboveTheTakenSlots_isAnEnlargement() {
        Campaign c = campaign(LifecycleStatus.CLOSED);
        c.setRedemptionsCount(4);
        c.setConfirmedCount(3);

        assertThatThrownBy(() -> service().reopen(c.getUuid(), new CampaignReopenRequest(4, null)))
                .hasMessage("campaign.reopen.not_enlarged");

        Campaign reopened = service().reopen(c.getUuid(), new CampaignReopenRequest(5, null));
        assertThat(reopened.getMaxRedemptions()).isEqualTo(5);
        assertThat(reopened.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
    }

    // ─── END_DATE close (job) ───────────────────────────────────────────────

    @Test
    void closeEnded_closesByEndDate_withoutActor_atTheEndInstant() {
        Campaign c = campaign(LifecycleStatus.SOLD_OUT);
        OffsetDateTime end = OffsetDateTime.now().minusHours(2);
        c.setEndsAt(end);
        when(repository.findDueForEndDateClose(any(), any())).thenReturn(List.of(c));
        when(repository.findForUpdateById(eq(7L))).thenReturn(Optional.of(c));

        int closed = service().closeEnded(OffsetDateTime.now());

        assertThat(closed).isEqualTo(1);
        assertThat(c.getLifecycleStatus()).isEqualTo(LifecycleStatus.CLOSED);
        assertThat(c.getClosedReason()).isEqualTo(CloseReason.END_DATE);
        assertThat(c.getClosedAt()).isEqualTo(end);
        assertThat(c.getClosedBy()).isNull();
        verify(entityManager).refresh(c);
        verify(redemptionService).onCampaignClosed(c);
    }

    @Test
    void activate_thatFillsTheQuota_followsUpTheQuotaFilledClose() {
        Campaign c = campaign(LifecycleStatus.DRAFT);
        c.setMaxRedemptions(1);
        c.setCloseOnQuotaFilled(true);
        c.setRedemptionsCount(1);
        c.setConfirmedCount(1);

        assertThat(service().activate(c.getUuid()).getClosedReason()).isEqualTo(CloseReason.QUOTA_FILLED);
        verify(redemptionService).onCampaignClosed(c);
    }

    // ─── Quota-driven state (Campaign.recomputeQuotaStatus) ─────────────────

    @Test
    void quotaFilled_closesOnlyWithTheCondition_otherwiseSoldOut() {
        Campaign withCondition = campaign(LifecycleStatus.ACTIVE);
        withCondition.setMaxRedemptions(1);
        withCondition.setCloseOnQuotaFilled(true);
        withCondition.setRedemptionsCount(1);
        withCondition.setConfirmedCount(1);
        withCondition.recomputeQuotaStatus(OffsetDateTime.now());
        assertThat(withCondition.getLifecycleStatus()).isEqualTo(LifecycleStatus.CLOSED);
        assertThat(withCondition.getClosedReason()).isEqualTo(CloseReason.QUOTA_FILLED);

        Campaign without = campaign(LifecycleStatus.ACTIVE);
        without.setMaxRedemptions(1);
        without.setRedemptionsCount(1);
        without.setConfirmedCount(1);
        without.recomputeQuotaStatus(OffsetDateTime.now());
        assertThat(without.getLifecycleStatus()).isEqualTo(LifecycleStatus.SOLD_OUT);

        without.setRedemptionsCount(0);
        without.setConfirmedCount(0);
        without.recomputeQuotaStatus(OffsetDateTime.now());
        assertThat(without.getLifecycleStatus()).isEqualTo(LifecycleStatus.ACTIVE);
    }

    @Test
    void openEndedCampaign_offersWhileActive() {
        Campaign c = campaign(LifecycleStatus.ACTIVE);
        c.setEndsAt(null);
        c.setEnabled(true);

        assertThat(c.isOffering(OffsetDateTime.now())).isTrue();
        assertThat(c.freeSlots()).isNull();

        c.setLifecycleStatus(LifecycleStatus.PAUSED);
        assertThat(c.isOffering(OffsetDateTime.now())).isFalse();
    }
}
