package com.fenixcore.optibienestar360.modules.document.generic;

import com.fenixcore.optibienestar360.core.audit.ReportAuditService;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.document.generic.controller.GenericDocumentController;
import com.fenixcore.optibienestar360.modules.document.generic.service.GenericRecordReportService;
import com.fenixcore.optibienestar360.modules.document.generic.service.GenericRecordResolverService;
import com.fenixcore.optibienestar360.modules.document.jasper.JasperFormat;
import com.fenixcore.optibienestar360.modules.document.jasper.JasperReportService;
import com.fenixcore.optibienestar360.modules.document.service.RenderedDocument;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import com.fenixcore.optibienestar360.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * "Generado por" on record documents: a JWT principal only carries the user's UUID, so the
 * printed name must be looked up and the raw UUID never reaches the document.
 */
class GenericDocumentControllerGeneratedByTest {

    private static final String RECORD_ID = "71901444-d1da-49de-871f-b840defb4e40";

    private GenericRecordReportService recordReportService;
    private UserRepository userRepository;
    private MessageSource messageSource;
    private GenericDocumentController controller;
    private CustomUserDetails actor;

    @BeforeEach
    void setUp() {
        recordReportService = mock(GenericRecordReportService.class);
        GenericRecordResolverService recordResolverService = mock(GenericRecordResolverService.class);
        userRepository = mock(UserRepository.class);
        messageSource = mock(MessageSource.class);

        when(recordResolverService.findRecordByTableAndId(any(), any())).thenReturn(new Object());
        when(recordReportService.generateGenericRecordDocument(any(), any(), any(), any(), any(), any()))
                .thenReturn(new RenderedDocument(new byte[]{1}, "application/pdf", "ficha.pdf"));
        when(messageSource.getMessage(eq("document.meta.system_user"), any(), any()))
                .thenReturn("Usuario del sistema");

        controller = new GenericDocumentController(
                recordReportService,
                recordResolverService,
                messageSource,
                mock(ReportAuditService.class),
                mock(JasperReportService.class),
                mock(DataSource.class),
                mock(PromoterRepository.class),
                mock(PlanRepository.class),
                mock(SystemConfigService.class),
                null,
                userRepository
        );
        actor = CustomUserDetails.fromJwt(UUID.randomUUID(), "jti", "es", List.of());
    }

    @Test
    void printsTheActorsDisplayName() {
        when(userRepository.findDisplayNameByUuid(actor.getUuid())).thenReturn(Optional.of("María Pérez"));

        controller.generateDocumentByRecord("allies", RECORD_ID, "PDF", null, null, actor);

        assertEquals("María Pérez", capturedGeneratedBy());
    }

    @Test
    void unknownUserFallsBackToSystemLabelNotTheUuid() {
        when(userRepository.findDisplayNameByUuid(actor.getUuid())).thenReturn(Optional.empty());

        controller.generateDocumentByRecord("allies", RECORD_ID, "PDF", null, null, actor);

        String generatedBy = capturedGeneratedBy();
        assertEquals("Usuario del sistema", generatedBy);
        assertNotEquals(actor.getUuid().toString(), generatedBy);
    }

    private String capturedGeneratedBy() {
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(recordReportService).generateGenericRecordDocument(
                any(), any(), any(), any(), captor.capture(), eq(JasperFormat.PDF));
        return captor.getValue();
    }
}
