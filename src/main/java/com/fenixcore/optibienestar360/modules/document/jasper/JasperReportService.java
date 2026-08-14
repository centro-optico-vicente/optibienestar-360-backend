package com.fenixcore.optibienestar360.modules.document.jasper;

import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import net.sf.jasperreports.export.SimpleXlsxReportConfiguration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
public class JasperReportService {

    /**
     * Generates Jasper report bytes from a classpath template and a data collection.
     *
     * @param reportPath Relative classpath path (e.g. "reports/my_report.jasper")
     * @param parameters Parameter map for the report
     * @param data       Collection of DTO objects for JRBeanCollectionDataSource
     * @param format     PDF or XLSX
     * @return Generated file byte array
     */
    public byte[] generateReport(String reportPath, Map<String, Object> parameters, Collection<?> data, JasperFormat format) {
        try (InputStream templateStream = new ClassPathResource(reportPath).getInputStream()) {
            return generateReportFromStream(templateStream, parameters, data, format);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not load report template from classpath: " + reportPath, e);
        }
    }

    /**
     * Generates Jasper report bytes using a direct JDBC connection.
     *
     * @param reportPath Relative classpath path (e.g. "reports/my_report.jasper")
     * @param parameters Parameter map for the report
     * @param connection Database connection
     * @param format     PDF or XLSX
     * @return Generated file byte array
     */
    public byte[] generateReportWithConnection(String reportPath, Map<String, Object> parameters, Connection connection, JasperFormat format) {
        try (InputStream templateStream = new ClassPathResource(reportPath).getInputStream()) {
            return generateReportFromStreamWithConnection(templateStream, parameters, connection, format);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not load report template from classpath: " + reportPath, e);
        }
    }

    /**
     * Generates a report from an InputStream by loading a precompiled .jasper file or compiling .jrxml.
     */
    public byte[] generateReportFromStream(InputStream templateStream, Map<String, Object> parameters, Collection<?> data, JasperFormat format) {
        try {
            JasperReport jasperReport = loadJasperReport(templateStream);
            Map<String, Object> params = parameters != null ? new HashMap<>(parameters) : new HashMap<>();
            JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(data != null ? data : Collections.emptyList());

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, dataSource);
            return exportJasperPrint(jasperPrint, format);
        } catch (Exception e) {
            throw new RuntimeException("Error processing Jasper report: " + e.getMessage(), e);
        }
    }

    /**
     * Generates a report from an InputStream using a JDBC connection.
     */
    public byte[] generateReportFromStreamWithConnection(InputStream templateStream, Map<String, Object> parameters, Connection connection, JasperFormat format) {
        try {
            JasperReport jasperReport = loadJasperReport(templateStream);
            Map<String, Object> params = parameters != null ? new HashMap<>(parameters) : new HashMap<>();

            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, params, connection);
            return exportJasperPrint(jasperPrint, format);
        } catch (Exception e) {
            throw new RuntimeException("Error processing Jasper report with JDBC connection: " + e.getMessage(), e);
        }
    }

    private JasperReport loadJasperReport(InputStream templateStream) throws JRException {
        try {
            byte[] bytes = templateStream.readAllBytes();
            try {
                return (JasperReport) JRLoader.loadObject(new ByteArrayInputStream(bytes));
            } catch (Exception ex) {
                return JasperCompileManager.compileReport(new ByteArrayInputStream(bytes));
            }
        } catch (IOException e) {
            throw new JRException("Error reading Jasper template stream", e);
        }
    }

    public byte[] exportJasperPrint(JasperPrint jasperPrint, JasperFormat format) throws JRException {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (format == JasperFormat.PDF) {
                JRPdfExporter exporter = new JRPdfExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));

                SimplePdfExporterConfiguration pdfConfig = new SimplePdfExporterConfiguration();
                pdfConfig.setCreatingBatchModeBookmarks(true);
                exporter.setConfiguration(pdfConfig);

                exporter.exportReport();
            } else if (format == JasperFormat.XLSX) {
                JRXlsxExporter exporter = new JRXlsxExporter();
                exporter.setExporterInput(new SimpleExporterInput(jasperPrint));
                exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(out));

                SimpleXlsxReportConfiguration xlsxConfig = new SimpleXlsxReportConfiguration();
                xlsxConfig.setOnePagePerSheet(false);
                xlsxConfig.setRemoveEmptySpaceBetweenRows(true);
                xlsxConfig.setWhitePageBackground(false);
                xlsxConfig.setDetectCellType(true);
                exporter.setConfiguration(xlsxConfig);

                exporter.exportReport();
            } else {
                throw new IllegalArgumentException("Unsupported Jasper export format: " + format);
            }
            return out.toByteArray();
        } catch (Exception e) {
            throw new JRException("Error exporting Jasper report to " + format, e);
        }
    }
}
