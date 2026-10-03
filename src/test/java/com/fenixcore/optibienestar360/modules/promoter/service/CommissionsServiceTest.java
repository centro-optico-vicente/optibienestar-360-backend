package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.currency.service.ConversionEnricher;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.mapper.CommissionMapper;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionPeriodSummaryRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.CommissionRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers {@link CommissionsService#voidCommission} — the one mutation this
 * otherwise read-only admin service owns (see {@code AdminCommissionController}).
 */
@ExtendWith(MockitoExtension.class)
class CommissionsServiceTest {

    @Mock private CommissionRepository repository;
    @Mock private CommissionMapper mapper;
    @Mock private CommissionPeriodSummaryRepository periodSummaryRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private CurrencyRepository currencyRepository;
    @Mock private ConversionEnricher conversionEnricher;
    @Mock private DefaultSortResolver defaultSortResolver;
    @Mock private CommissionAuditRecorder auditRecorder;

    @InjectMocks private CommissionsService service;

    @Test
    void voidCommission_marksPendingAsVoided_andRecordsAudit() {
        UUID uuid = UUID.randomUUID();
        Commission commission = new Commission();
        commission.setUuid(uuid);
        commission.setStatus(Commission.CommissionStatus.PENDING.name());

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(commission));
        when(mapper.toDto(commission)).thenReturn(org.mockito.Mockito.mock(CommissionDto.class));
        when(conversionEnricher.toOfficial(any(), any())).thenReturn(ConversionEnricher.none());
        Map<String, Object> before = Map.of("status", "PENDING");
        Map<String, Object> after = Map.of("status", "VOIDED");
        when(auditRecorder.snapshot(commission)).thenReturn(before, after);

        service.voidCommission(uuid, "Incumplimiento de meta");

        assertThat(commission.getStatus()).isEqualTo(Commission.CommissionStatus.VOIDED.name());
        assertThat(commission.getVoidReason()).isEqualTo("Incumplimiento de meta");
        assertThat(commission.getVoidedAt()).isNotNull();
        verify(auditRecorder).recordUpdate(eq(uuid), eq(before), eq(after));
    }

    @Test
    void voidCommission_rejectsNonPending() {
        UUID uuid = UUID.randomUUID();
        Commission commission = new Commission();
        commission.setUuid(uuid);
        commission.setStatus(Commission.CommissionStatus.PAID.name());

        when(repository.findByUuid(uuid)).thenReturn(Optional.of(commission));

        assertThatThrownBy(() -> service.voidCommission(uuid, "too late"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("commission.void.not_pending");

        verify(auditRecorder, never()).recordUpdate(any(), any(), any());
        assertThat(commission.getStatus()).isEqualTo(Commission.CommissionStatus.PAID.name());
    }

    @Test
    void voidBulk_voidsEveryPendingCommission_withTheSharedReason() {
        Commission a = commission(Commission.CommissionStatus.PENDING);
        Commission b = commission(Commission.CommissionStatus.PENDING);
        when(repository.findByUuid(a.getUuid())).thenReturn(Optional.of(a));
        when(repository.findByUuid(b.getUuid())).thenReturn(Optional.of(b));
        when(mapper.toDto(any())).thenReturn(org.mockito.Mockito.mock(CommissionDto.class));
        when(conversionEnricher.toOfficial(any(), any())).thenReturn(ConversionEnricher.none());

        service.voidBulk(List.of(a.getUuid(), b.getUuid()), "Baja por falta grave");

        assertThat(List.of(a, b)).allSatisfy(c -> {
            assertThat(c.getStatus()).isEqualTo(Commission.CommissionStatus.VOIDED.name());
            assertThat(c.getVoidReason()).isEqualTo("Baja por falta grave");
        });
        verify(auditRecorder, times(2)).recordUpdate(any(), any(), any());
    }

    @Test
    void voidBulk_isAllOrNothing_whenAnyCommissionIsNotPending() {
        Commission pending = commission(Commission.CommissionStatus.PENDING);
        Commission paid = commission(Commission.CommissionStatus.PAID);
        when(repository.findByUuid(pending.getUuid())).thenReturn(Optional.of(pending));
        when(repository.findByUuid(paid.getUuid())).thenReturn(Optional.of(paid));

        assertThatThrownBy(() -> service.voidBulk(List.of(pending.getUuid(), paid.getUuid()), "x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("commission.void.not_pending");

        assertThat(pending.getStatus()).isEqualTo(Commission.CommissionStatus.PENDING.name());
        verify(auditRecorder, never()).recordUpdate(any(), any(), any());
    }

    private static Commission commission(Commission.CommissionStatus status) {
        Commission c = new Commission();
        c.setUuid(UUID.randomUUID());
        c.setStatus(status.name());
        return c;
    }
}
