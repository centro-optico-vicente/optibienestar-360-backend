package com.fenixcore.optibienestar360.modules.document.generic;

import com.fenixcore.optibienestar360.core.audit.ReportAuditService;
import com.fenixcore.optibienestar360.modules.document.generic.controller.GenericDocumentController;
import com.fenixcore.optibienestar360.modules.document.generic.service.GenericRecordReportService;
import com.fenixcore.optibienestar360.modules.document.generic.service.GenericRecordResolverService;
import com.fenixcore.optibienestar360.modules.document.jasper.JasperFormat;
import com.fenixcore.optibienestar360.modules.document.jasper.JasperReportService;
import com.fenixcore.optibienestar360.modules.membership.entity.Plan;
import com.fenixcore.optibienestar360.modules.membership.repository.PlanRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GenericDocumentControllerJasperTest {

    private GenericRecordReportService recordReportService;
    private GenericRecordResolverService recordResolverService;
    private MessageSource messageSource;
    private ReportAuditService reportAuditService;
    private JasperReportService jasperReportService;
    private DataSource dataSource;
    private PromoterRepository promoterRepository;
    private PlanRepository planRepository;
    private com.fenixcore.optibienestar360.modules.system.service.SystemConfigService systemConfigService;
    private Connection mockConnection;

    private GenericDocumentController controller;

    @BeforeEach
    void setUp() throws Exception {
        recordReportService = mock(GenericRecordReportService.class);
        recordResolverService = mock(GenericRecordResolverService.class);
        messageSource = mock(MessageSource.class);
        reportAuditService = mock(ReportAuditService.class);
        jasperReportService = mock(JasperReportService.class);
        dataSource = mock(DataSource.class);
        promoterRepository = mock(PromoterRepository.class);
        planRepository = mock(PlanRepository.class);
        systemConfigService = mock(com.fenixcore.optibienestar360.modules.system.service.SystemConfigService.class);
        mockConnection = mock(Connection.class);

        when(dataSource.getConnection()).thenReturn(mockConnection);
        when(systemConfigService.getReportFooter()).thenReturn("Pie de página mock");

        controller = new GenericDocumentController(
                recordReportService,
                recordResolverService,
                messageSource,
                reportAuditService,
                jasperReportService,
                dataSource,
                promoterRepository,
                planRepository,
                systemConfigService
        );
    }

    @Test
    @DisplayName("Should generate commissions report in PDF and record audit")
    void testGenerateCommissionsJasperReportPdf() {
        byte[] fakePdf = "%PDF-1.4 fake".getBytes();
        when(jasperReportService.generateReportWithConnection(
                eq("reports/reporte-comisiones.jrxml"),
                anyMap(),
                eq(mockConnection),
                eq(JasperFormat.PDF)
        )).thenReturn(fakePdf);

        UUID promoterUuid = UUID.randomUUID();
        Promoter promoter = mock(Promoter.class);
        when(promoter.getId()).thenReturn(42L);
        when(promoterRepository.findByUuid(promoterUuid)).thenReturn(Optional.of(promoter));

        ResponseEntity<byte[]> response = controller.generateJasperReport(
                "comisiones",
                "PDF",
                "2026-07-01",
                "2026-09-30",
                promoterUuid.toString(),
                "PAID",
                "MONTHLY",
                null,
                null,
                null,
                "Empresa Test"
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(fakePdf, response.getBody());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("reporte-comisiones_"));
        assertEquals("application/pdf", response.getHeaders().getContentType().toString());

        verify(reportAuditService).recordGeneration(
                eq("JASPER"),
                eq("commission"),
                isNull(),
                isNull(),
                eq("PDF"),
                anyMap(),
                eq(fakePdf),
                anyString(),
                eq("application/pdf")
        );
    }

    @Test
    @DisplayName("Should generate payments report in XLSX and record audit")
    void testGeneratePaymentsJasperReportXlsx() {
        byte[] fakeXlsx = "PK fake xlsx".getBytes();
        when(jasperReportService.generateReportWithConnection(
                eq("reports/reporte-pagos.jrxml"),
                anyMap(),
                eq(mockConnection),
                eq(JasperFormat.XLSX)
        )).thenReturn(fakeXlsx);

        UUID planUuid = UUID.randomUUID();
        Plan plan = mock(Plan.class);
        when(plan.getId()).thenReturn(10L);
        when(planRepository.findByUuid(planUuid)).thenReturn(Optional.of(plan));

        ResponseEntity<byte[]> response = controller.generateJasperReport(
                "pagos",
                "XLSX",
                "2026-08-01",
                "2026-08-31",
                null,
                "APPROVED",
                null,
                "ZELLE",
                planUuid.toString(),
                null,
                null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(fakeXlsx, response.getBody());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("reporte-pagos-afiliados_"));
        assertTrue(response.getHeaders().getContentType().toString().contains("spreadsheetml"));

        verify(reportAuditService).recordGeneration(
                eq("JASPER"),
                eq("payment"),
                isNull(),
                isNull(),
                eq("XLSX"),
                anyMap(),
                eq(fakeXlsx),
                anyString(),
                eq("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        );
    }

    @Test
    @DisplayName("Should generate commission payouts report in PDF and record audit")
    void testGenerateCommissionPayoutsJasperReportPdf() {
        byte[] fakePdf = "%PDF-1.4 payouts fake".getBytes();
        when(jasperReportService.generateReportWithConnection(
                eq("reports/reporte-pagos-comisiones.jrxml"),
                anyMap(),
                eq(mockConnection),
                eq(JasperFormat.PDF)
        )).thenReturn(fakePdf);

        ResponseEntity<byte[]> response = controller.generateJasperReport(
                "pagos-comisiones",
                "PDF",
                "2026-07-01",
                "2026-08-31",
                null,
                null,
                null,
                null,
                null,
                "PAYOUT-2026",
                null
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(fakePdf, response.getBody());
        assertTrue(response.getHeaders().getFirst("Content-Disposition").contains("reporte-pagos-comisiones_"));
    }

    @Test
    @DisplayName("Should reject unknown report name with IllegalArgumentException")
    void testUnknownReportName() {
        assertThrows(IllegalArgumentException.class, () ->
                controller.generateJasperReport("inventario", "PDF", null, null, null, null, null, null, null, null, null)
        );
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should enforce granular permission: user with COMMISSION_REPORT_GENERATE cannot generate payments report")
    void testCommissionsUserCannotGeneratePayments() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "user", "pass", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("COMMISSION_REPORT_GENERATE")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                controller.generateJasperReport("pagos", "PDF", null, null, null, null, null, null, null, null, null)
        );
    }

    @Test
    @DisplayName("Should enforce granular permission: user with PAYMENT_REPORT_GENERATE cannot generate commissions report")
    void testPaymentsUserCannotGenerateCommissions() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "user", "pass", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("PAYMENT_REPORT_GENERATE")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                controller.generateJasperReport("comisiones", "PDF", null, null, null, null, null, null, null, null, null)
        );
    }

    @Test
    @DisplayName("Should allow user with REPORT_REPORT_GENERATE to generate all reports")
    void testGlobalReportGenerateCanAccessBoth() {
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                "admin", "pass", java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("REPORT_REPORT_GENERATE")));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        byte[] fakePdf = "%PDF-1.4 fake".getBytes();
        when(jasperReportService.generateReportWithConnection(anyString(), anyMap(), any(), eq(JasperFormat.PDF)))
                .thenReturn(fakePdf);

        assertDoesNotThrow(() -> controller.generateJasperReport("comisiones", "PDF", null, null, null, null, null, null, null, null, null));
        assertDoesNotThrow(() -> controller.generateJasperReport("pagos", "PDF", null, null, null, null, null, null, null, null, null));
        assertDoesNotThrow(() -> controller.generateJasperReport("pagos-comisiones", "PDF", null, null, null, null, null, null, null, null, null));
    }
}
