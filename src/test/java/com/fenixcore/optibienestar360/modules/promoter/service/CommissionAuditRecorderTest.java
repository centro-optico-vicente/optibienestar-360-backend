package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.AuditContextResolver;
import com.fenixcore.optibienestar360.core.audit.DataChangeAuditWriter;
import com.fenixcore.optibienestar360.core.audit.EntityConfigService;
import com.fenixcore.optibienestar360.modules.promoter.dto.CommissionDto;
import com.fenixcore.optibienestar360.modules.promoter.entity.Commission;
import com.fenixcore.optibienestar360.modules.promoter.mapper.CommissionMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link CommissionAuditRecorder} is the explicit (non-AOP) stand-in for
 * {@code DataChangeAuditAspect} on the three write paths that don't fit its
 * single-record uuid-arg CRUD shape — see the class javadoc.
 */
@ExtendWith(MockitoExtension.class)
class CommissionAuditRecorderTest {

    @Mock private EntityConfigService entityConfigService;
    @Mock private AuditContextResolver auditContextResolver;
    @Mock private DataChangeAuditWriter dataChangeAuditWriter;
    @Mock private CommissionMapper commissionMapper;
    @Mock private ObjectMapper objectMapper;

    @InjectMocks private CommissionAuditRecorder recorder;

    @Test
    void recordCreate_writesNothing_whenCreateAuditDisabled() {
        when(entityConfigService.isEnabled("commission", AuditAction.CREATE)).thenReturn(false);

        recorder.recordCreate(new Commission());

        verify(dataChangeAuditWriter, never()).write(any(), any(), any(), any(), any(), any(), any());
        verifyNoInteractions(commissionMapper, auditContextResolver);
    }

    @Test
    void recordCreate_writesWithNullBefore_andActorId_whenEnabled() {
        Commission commission = new Commission();
        commission.setUuid(UUID.randomUUID());
        CommissionDto dto = org.mockito.Mockito.mock(CommissionDto.class);
        Map<String, Object> afterJson = Map.of("status", "PENDING");

        when(entityConfigService.isEnabled("commission", AuditAction.CREATE)).thenReturn(true);
        when(entityConfigService.captureBeforeAfter("commission")).thenReturn(true);
        when(commissionMapper.toDto(commission)).thenReturn(dto);
        when(objectMapper.convertValue(eq(dto), eq(Map.class))).thenReturn(afterJson);
        when(auditContextResolver.resolveActorId()).thenReturn(Optional.of(42L));

        recorder.recordCreate(commission);

        verify(dataChangeAuditWriter).write(
                eq("commission"), eq(commission.getUuid()), eq(AuditAction.CREATE),
                isNull(), eq(afterJson), eq(42L), isNull());
    }

    @Test
    void recordUpdate_writesNothing_whenUpdateAuditDisabled() {
        when(entityConfigService.isEnabled("commission", AuditAction.UPDATE)).thenReturn(false);

        recorder.recordUpdate(UUID.randomUUID(), Map.of("status", "PENDING"), Map.of("status", "PAID"));

        verify(dataChangeAuditWriter, never()).write(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void recordUpdate_writesBeforeAndAfter_whenCaptureEnabled() {
        UUID uuid = UUID.randomUUID();
        Map<String, Object> before = Map.of("status", "PENDING");
        Map<String, Object> after = Map.of("status", "PAID");

        when(entityConfigService.isEnabled("commission", AuditAction.UPDATE)).thenReturn(true);
        when(entityConfigService.captureBeforeAfter("commission")).thenReturn(true);
        when(auditContextResolver.resolveActorId()).thenReturn(Optional.empty());

        recorder.recordUpdate(uuid, before, after);

        verify(dataChangeAuditWriter).write(
                eq("commission"), eq(uuid), eq(AuditAction.UPDATE), eq(before), eq(after), isNull(), isNull());
    }

    @Test
    void recordUpdate_omitsSnapshots_whenCaptureBeforeAfterDisabled() {
        UUID uuid = UUID.randomUUID();

        when(entityConfigService.isEnabled("commission", AuditAction.UPDATE)).thenReturn(true);
        when(entityConfigService.captureBeforeAfter("commission")).thenReturn(false);
        when(auditContextResolver.resolveActorId()).thenReturn(Optional.empty());

        recorder.recordUpdate(uuid, Map.of("status", "PENDING"), Map.of("status", "PAID"));

        verify(dataChangeAuditWriter).write(
                eq("commission"), eq(uuid), eq(AuditAction.UPDATE), isNull(), isNull(), isNull(), isNull());
    }
}
