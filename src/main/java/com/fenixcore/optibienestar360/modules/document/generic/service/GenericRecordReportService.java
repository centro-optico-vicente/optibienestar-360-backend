package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericRecordModel;
import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericTableModel;
import com.fenixcore.optibienestar360.modules.document.jasper.JasperFormat;
import com.fenixcore.optibienestar360.modules.document.service.RenderedDocument;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class GenericRecordReportService {

    private final GenericEntityExtractorService extractorService;
    private final GenericHtmlPdfService htmlPdfService;
    private final GenericXlsxExporterService xlsxExporterService;

    public GenericRecordReportService(
            GenericEntityExtractorService extractorService,
            GenericHtmlPdfService htmlPdfService,
            GenericXlsxExporterService xlsxExporterService
    ) {
        this.extractorService = extractorService;
        this.htmlPdfService = htmlPdfService;
        this.xlsxExporterService = xlsxExporterService;
    }

    /**
     * Generates a generic record card document in PDF or XLSX format.
     */
    public RenderedDocument generateGenericRecordDocument(
            Object targetObject,
            String title,
            String subtitle,
            String identifier,
            String generatedBy,
            JasperFormat format
    ) {
        GenericRecordModel model = extractorService.extractModel(targetObject, title, subtitle, identifier, generatedBy);

        byte[] bytes;
        String contentType;
        String extension;

        if (format == JasperFormat.XLSX) {
            bytes = xlsxExporterService.generateXlsx(model);
            contentType = JasperFormat.XLSX.getContentType();
            extension = JasperFormat.XLSX.getFileExtension();
        } else {
            bytes = htmlPdfService.generatePdf(model, "documents/generic_record_card");
            contentType = JasperFormat.PDF.getContentType();
            extension = JasperFormat.PDF.getFileExtension();
        }

        String safeTitle = (model.title() != null ? model.title() : "record").toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        String fileName = safeTitle + "_" + (identifier != null ? identifier : "item") + extension;

        return new RenderedDocument(bytes, contentType, fileName);
    }

    /**
     * Generates a generic table list document in PDF or XLSX format.
     */
    public RenderedDocument generateGenericTableDocument(
            Collection<?> collection,
            String title,
            String subtitle,
            String generatedBy,
            JasperFormat format
    ) {
        GenericTableModel model = extractorService.extractTableModel(collection, title, subtitle, generatedBy);

        byte[] bytes;
        String contentType;
        String extension;

        if (format == JasperFormat.XLSX) {
            bytes = xlsxExporterService.generateTableXlsx(model);
            contentType = JasperFormat.XLSX.getContentType();
            extension = JasperFormat.XLSX.getFileExtension();
        } else {
            bytes = htmlPdfService.generatePdf(model, "documents/generic_table_list");
            contentType = JasperFormat.PDF.getContentType();
            extension = JasperFormat.PDF.getFileExtension();
        }

        String safeTitle = (model.title() != null ? model.title() : "list").toLowerCase().replaceAll("[^a-z0-9_-]", "_");
        String fileName = safeTitle + extension;

        return new RenderedDocument(bytes, contentType, fileName);
    }
}
