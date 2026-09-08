package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditContextResolver;
import com.fenixcore.optibienestar360.core.audit.DataChangeAuditWriter;
import com.fenixcore.optibienestar360.core.audit.EntityConfigService;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionApprovalActionResponse;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionApprovalGroupDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.AppliesTo;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission.CommissionStatus;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterHierarchyOverride.OverrideStatus;
import com.fenixcore.optibienestar360.modules.promoter.mapper.CommissionMapper;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterHierarchyOverrideRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommissionApprovalService} (V107, hub plan
 * ".ai/plans/2026-09-07-hierarchical-commissions-plan.md" §4, PR5) — the
 * row-level approve/reject gate and its cascade-void of dependent hierarchy
 * overrides.
 */
@ExtendWith(MockitoExtension.class)
class CommissionApprovalServiceTest {

    @Mock private CommissionRepository commissionRepository;
    @Mock private PromoterHierarchyOverrideRepository overrideRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityConfigService entityConfigService;
    @Mock private AuditContextResolver auditContextResolver;
    @Mock private DataChangeAuditWriter dataChangeAuditWriter;
    @Mock private CommissionMapper commissionMapper;

    /** Real recorder wired with mocked collaborators — same "built per-call" reasoning as elsewhere. */
    private CommissionAuditRecorder auditRecorder() {
        return new CommissionAuditRecorder(entityConfigService, auditContextResolver, dataChangeAuditWriter,
                commissionMapper, new ObjectMapper());
    }

    private CommissionApprovalService service() {
        return new CommissionApprovalService(commissionRepository, overrideRepository, userRepository, auditRecorder());
    }

    private static Promoter promoter(Long id, String name) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setDisplayName(name);
        p.setReferralCode(name.toUpperCase());
        return p;
    }

    private static Commission commission(Long id, Promoter promoter, String status) {
        Commission c = new Commission();
        c.setId(id);
        c.setUuid(UUID.randomUUID());
        c.setPromoter(promoter);
        c.setAppliesTo(AppliesTo.INSCRIPTION);
        c.setAmount(new BigDecimal("50.00"));
        Currency usd = new Currency();
        usd.setCode("USD");
        c.setCurrency(usd);
        c.setPeriodStart(LocalDate.of(2026, 6, 1));
        c.setPeriodEnd(LocalDate.of(2026, 6, 30));
        c.setStatus(status);
        return c;
    }

    @Test
    void approveRowsSetsApprovedStatusAndActor() {
        Promoter promoter = promoter(1L, "ase");
        Commission pending = commission(10L, promoter, CommissionStatus.PENDING.name());
        User actor = new User();
        actor.setId(5L);
        actor.setUuid(UUID.randomUUID());

        lenient().when(entityConfigService.isEnabled(any(), any())).thenReturn(false);
        when(commissionRepository.findByUuidIn(List.of(pending.getUuid()))).thenReturn(List.of(pending));
        when(userRepository.findByUuid(actor.getUuid())).thenReturn(Optional.of(actor));

        CommissionApprovalActionResponse response = service().approveRows(List.of(pending.getUuid()), actor.getUuid());

        assertThat(pending.getStatus()).isEqualTo(CommissionStatus.APPROVED.name());
        assertThat(pending.getApprovedBy()).isSameAs(actor);
        assertThat(pending.getApprovedAt()).isNotNull();
        assertThat(response.cascadedOverridesVoided()).isZero();
    }

    @Test
    void approveRowsRejectsWhenAnyRowIsNotPending() {
        Promoter promoter = promoter(1L, "ase");
        Commission alreadyApproved = commission(10L, promoter, CommissionStatus.APPROVED.name());

        when(commissionRepository.findByUuidIn(List.of(alreadyApproved.getUuid()))).thenReturn(List.of(alreadyApproved));

        assertThatThrownBy(() -> service().approveRows(List.of(alreadyApproved.getUuid()), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not_pending");
    }

    @Test
    void rejectRowsSetsRejectedStatusWithReasonAndVoidsDependentOverridesRecursively() {
        Promoter promoter = promoter(1L, "ase");
        Commission pending = commission(10L, promoter, CommissionStatus.PENDING.name());

        PromoterHierarchyOverride level2 = new PromoterHierarchyOverride();
        level2.setId(100L);
        level2.setUuid(UUID.randomUUID());
        level2.setStatus(OverrideStatus.PENDING.name());

        PromoterHierarchyOverride level3 = new PromoterHierarchyOverride();
        level3.setId(101L);
        level3.setUuid(UUID.randomUUID());
        level3.setStatus(OverrideStatus.PENDING.name());

        lenient().when(entityConfigService.isEnabled(any(), any())).thenReturn(false);
        when(commissionRepository.findByUuidIn(List.of(pending.getUuid()))).thenReturn(List.of(pending));
        when(overrideRepository.findBySourceCommissionId(10L)).thenReturn(List.of(level2));
        when(overrideRepository.findBySourceOverrideId(100L)).thenReturn(List.of(level3));
        when(overrideRepository.findBySourceOverrideId(101L)).thenReturn(List.of());

        CommissionApprovalActionResponse response = service().rejectRows(
                List.of(pending.getUuid()), null, "Non-compliance found");

        assertThat(pending.getStatus()).isEqualTo(CommissionStatus.REJECTED.name());
        assertThat(pending.getRejectionReason()).isEqualTo("Non-compliance found");
        assertThat(level2.getStatus()).isEqualTo(OverrideStatus.VOIDED.name());
        assertThat(level3.getStatus()).isEqualTo(OverrideStatus.VOIDED.name());
        assertThat(response.cascadedOverridesVoided()).isEqualTo(2);
    }

    @Test
    void rejectRowsStopsCascadeAtAnAlreadyPaidOverride_neverReversingSettledMoney() {
        Promoter promoter = promoter(1L, "ase");
        Commission pending = commission(10L, promoter, CommissionStatus.PENDING.name());

        PromoterHierarchyOverride alreadyPaid = new PromoterHierarchyOverride();
        alreadyPaid.setId(100L);
        alreadyPaid.setUuid(UUID.randomUUID());
        alreadyPaid.setStatus(OverrideStatus.PAID.name());

        lenient().when(entityConfigService.isEnabled(any(), any())).thenReturn(false);
        when(commissionRepository.findByUuidIn(List.of(pending.getUuid()))).thenReturn(List.of(pending));
        when(overrideRepository.findBySourceCommissionId(10L)).thenReturn(List.of(alreadyPaid));

        CommissionApprovalActionResponse response = service().rejectRows(List.of(pending.getUuid()), null, "x");

        assertThat(alreadyPaid.getStatus()).isEqualTo(OverrideStatus.PAID.name()); // untouched
        assertThat(response.cascadedOverridesVoided()).isZero();
    }

    @Test
    void approvalQueueGroupsByPromoterAndSumsOnlyCheckedRows() {
        Promoter promoter = promoter(1L, "ase");
        Commission pendingRow = commission(10L, promoter, CommissionStatus.PENDING.name());
        Commission approvedRow = commission(11L, promoter, CommissionStatus.APPROVED.name());
        Commission rejectedRow = commission(12L, promoter, CommissionStatus.REJECTED.name());

        when(commissionRepository.findAllForPeriod(LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30)))
                .thenReturn(List.of(pendingRow, approvedRow, rejectedRow));

        List<CommissionApprovalGroupDto> groups = service().approvalQueue(
                LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 30));

        assertThat(groups).hasSize(1);
        CommissionApprovalGroupDto group = groups.get(0);
        assertThat(group.rows()).hasSize(3);
        // 50.00 (PENDING, checked) + 50.00 (APPROVED, checked) — the REJECTED row is excluded.
        assertThat(group.periodTotal()).isEqualByComparingTo("100.00");

        var rejectedDto = group.rows().stream().filter(r -> r.uuid().equals(rejectedRow.getUuid())).findFirst().orElseThrow();
        assertThat(rejectedDto.locked()).isTrue();
        assertThat(rejectedDto.checked()).isFalse();

        var pendingDto = group.rows().stream().filter(r -> r.uuid().equals(pendingRow.getUuid())).findFirst().orElseThrow();
        assertThat(pendingDto.locked()).isFalse();
        assertThat(pendingDto.checked()).isTrue();
    }
}
