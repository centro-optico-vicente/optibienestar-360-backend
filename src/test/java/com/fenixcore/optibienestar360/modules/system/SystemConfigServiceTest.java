package com.fenixcore.optibienestar360.modules.system;

import com.fenixcore.optibienestar360.core.audit.AuditMode;
import com.fenixcore.optibienestar360.modules.system.dto.UpdateSystemConfigRequest;
import com.fenixcore.optibienestar360.modules.system.entity.SystemConfig;
import com.fenixcore.optibienestar360.modules.system.repository.SystemConfigRepository;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemConfigServiceTest {

    @Mock
    private SystemConfigRepository repository;

    @InjectMocks
    private SystemConfigService service;

    private SystemConfig sampleConfig;

    @BeforeEach
    void setUp() {
        sampleConfig = new SystemConfig();
        sampleConfig.setReportFooter("Centro Óptico Vicente - Personalizado");
    }

    @Test
    @DisplayName("Should return active report footer from repository when present")
    void testGetReportFooterPresent() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(sampleConfig));

        String footer = service.getReportFooter();

        assertEquals("Centro Óptico Vicente - Personalizado", footer);
        verify(repository, times(1)).findFirstByActiveTrue();
    }

    @Test
    @DisplayName("Should return null report footer when active config is empty or missing")
    void testGetReportFooterDefaultFallback() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.empty());

        String footer = service.getReportFooter();

        assertNull(footer);
    }

    @Test
    @DisplayName("Should update existing active config record (Singleton pattern)")
    void testUpdateReportFooterExisting() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(sampleConfig));
        when(repository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemConfig updated = service.updateReportFooter("Nuevo Pie de Pagina");

        assertNotNull(updated);
        assertEquals("Nuevo Pie de Pagina", updated.getReportFooter());
        verify(repository).save(sampleConfig);
    }

    @Test
    @DisplayName("Should default audit overrides to PER_ENTITY/enabled on a fresh config")
    void testAuditOverridesDefaults() {
        SystemConfig fresh = new SystemConfig();

        assertEquals(AuditMode.PER_ENTITY, fresh.getDataChangeAuditMode());
        assertEquals(AuditMode.PER_ENTITY, fresh.getReportAuditMode());
        assertTrue(fresh.isLoginAuditEnabled());
        assertEquals(30, fresh.getLoginSessionExpirationDays());
    }

    @Test
    @DisplayName("Should update only the audit overrides sent in the request, leaving the rest untouched")
    void testUpdateSystemConfigPartialAuditOverrides() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(sampleConfig));
        when(repository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemConfig updated = service.updateSystemConfig(
                new UpdateSystemConfigRequest(null, AuditMode.FORCE_DISABLED, null, false, null));

        assertEquals("Centro Óptico Vicente - Personalizado", updated.getReportFooter());
        assertEquals(AuditMode.FORCE_DISABLED, updated.getDataChangeAuditMode());
        assertEquals(AuditMode.PER_ENTITY, updated.getReportAuditMode());
        assertFalse(updated.isLoginAuditEnabled());
        assertEquals(30, updated.getLoginSessionExpirationDays());
        verify(repository).save(sampleConfig);
    }

    @Test
    @DisplayName("Should update login session expiration days when sent, leaving other fields untouched")
    void testUpdateSystemConfigLoginSessionExpirationDays() {
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(sampleConfig));
        when(repository.save(any(SystemConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemConfig updated = service.updateSystemConfig(
                new UpdateSystemConfigRequest(null, null, null, null, 90));

        assertEquals(90, updated.getLoginSessionExpirationDays());
        assertEquals(AuditMode.PER_ENTITY, updated.getDataChangeAuditMode());
        verify(repository).save(sampleConfig);
    }
}
