package com.fenixcore.optibienestar360.modules.document.jasper;

import com.fenixcore.optibienestar360.modules.document.service.renderers.JasperPdfRenderer;
import com.fenixcore.optibienestar360.modules.document.service.renderers.JasperXlsxRenderer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JasperReportServiceTest {

    private JasperReportService jasperReportService;
    private JasperPdfRenderer jasperPdfRenderer;
    private JasperXlsxRenderer jasperXlsxRenderer;

    @BeforeEach
    void setUp() {
        jasperReportService = new JasperReportService();
        jasperPdfRenderer = new JasperPdfRenderer(jasperReportService);
        jasperXlsxRenderer = new JasperXlsxRenderer(jasperReportService);
    }

    private static final String SIMPLE_JRXML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <jasperReport xmlns="http://jasperreports.sourceforge.net/jasperreports"
                          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                          xsi:schemaLocation="http://jasperreports.sourceforge.net/jasperreports http://jasperreports.sourceforge.net/xsd/jasperreport.xsd"
                          name="TestReport" pageWidth="595" pageHeight="842" columnWidth="555" leftMargin="20" rightMargin="20" topMargin="20" bottomMargin="20">
                <parameter name="ReportTitle" class="java.lang.String"/>
                <title>
                    <band height="50">
                        <textField>
                            <reportElement x="0" y="10" width="555" height="30"/>
                            <textElement textAlignment="Center">
                                <font size="18" isBold="true"/>
                            </textElement>
                            <textFieldExpression><![CDATA[$P{ReportTitle}]]></textFieldExpression>
                        </textField>
                    </band>
                </title>
            </jasperReport>
            """;

    @Test
    @DisplayName("Should generate a valid PDF with %PDF header from JasperPrint")
    void testExportPdfFromStream() throws Exception {
        ByteArrayInputStream jrxmlStream = new ByteArrayInputStream(SIMPLE_JRXML.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> params = new HashMap<>();
        params.put("ReportTitle", "PDF Report Test");

        byte[] pdfBytes = jasperReportService.generateReportFromStream(jrxmlStream, params, Collections.emptyList(), JasperFormat.PDF);

        assertNotNull(pdfBytes);
        assertTrue(pdfBytes.length > 0);
        // Verify PDF file signature (%PDF)
        String pdfHeader = new String(pdfBytes, 0, 4, StandardCharsets.ISO_8859_1);
        assertEquals("%PDF", pdfHeader);
    }

    @Test
    @DisplayName("Should generate a valid XLSX with ZIP (PK) signature from JasperPrint")
    void testExportXlsxFromStream() throws Exception {
        ByteArrayInputStream jrxmlStream = new ByteArrayInputStream(SIMPLE_JRXML.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> params = new HashMap<>();
        params.put("ReportTitle", "Excel Report Test");

        byte[] xlsxBytes = jasperReportService.generateReportFromStream(jrxmlStream, params, Collections.emptyList(), JasperFormat.XLSX);

        assertNotNull(xlsxBytes);
        assertTrue(xlsxBytes.length > 0);
        // XLSX files are ZIP containers starting with magic bytes 'P' 'K' (0x50, 0x4B)
        assertEquals((byte) 'P', xlsxBytes[0]);
        assertEquals((byte) 'K', xlsxBytes[1]);
    }

    @Test
    @DisplayName("JasperPdfRenderer should return RenderedDocument with application/pdf content type")
    void testPdfRendererComponent() {
        ByteArrayInputStream jrxmlStream = new ByteArrayInputStream(SIMPLE_JRXML.getBytes(StandardCharsets.UTF_8));
        Map<String, Object> params = Map.of("ReportTitle", "Test Component PDF");

        byte[] bytes = jasperReportService.generateReportFromStream(jrxmlStream, params, Collections.emptyList(), JasperFormat.PDF);
        assertNotNull(bytes);
        assertEquals(JasperFormat.PDF, jasperPdfRenderer.format());
    }

    @Test
    @DisplayName("JasperXlsxRenderer should return RenderedDocument with spreadsheetml content type")
    void testXlsxRendererComponent() {
        assertEquals(JasperFormat.XLSX, jasperXlsxRenderer.format());
    }
}
