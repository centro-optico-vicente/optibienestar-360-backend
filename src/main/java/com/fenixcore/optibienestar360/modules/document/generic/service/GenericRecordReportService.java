package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericRecordModel;
import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericTableModel;
import com.fenixcore.optibienestar360.modules.document.jasper.JasperFormat;
import com.fenixcore.optibienestar360.modules.document.service.RenderedDocument;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;

@Service
public class GenericRecordReportService {

    private final GenericEntityExtractorService extractorService;
    private final GenericHtmlPdfService htmlPdfService;
    private final GenericXlsxExporterService xlsxExporterService;
    private final MessageSource messageSource;

    public GenericRecordReportService(
            GenericEntityExtractorService extractorService,
            GenericHtmlPdfService htmlPdfService,
            GenericXlsxExporterService xlsxExporterService,
            MessageSource messageSource
    ) {
        this.extractorService = extractorService;
        this.htmlPdfService = htmlPdfService;
        this.xlsxExporterService = xlsxExporterService;
        this.messageSource = messageSource;
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

        Locale locale = LocaleContextHolder.getLocale();
        String defaultRecord = resolveMessage("document.filename.record", "record", locale);
        String defaultItem = resolveMessage("document.filename.item", "item", locale);

        String safeTitle = sanitizeFilename(model.title() != null ? model.title() : defaultRecord);
        String safeIdentifier = identifier != null ? sanitizeFilename(identifier) : defaultItem;
        String fileName = safeTitle + "_" + safeIdentifier + extension;

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

        Locale locale = LocaleContextHolder.getLocale();
        String defaultList = resolveMessage("document.filename.list", "list", locale);

        String safeTitle = sanitizeFilename(model.title() != null ? model.title() : defaultList);
        String fileName = safeTitle + extension;

        return new RenderedDocument(bytes, contentType, fileName);
    }

    private String resolveMessage(String key, String defaultMsg, Locale locale) {
        if (messageSource != null) {
            try {
                String msg = messageSource.getMessage(key, null, locale != null ? locale : Locale.ENGLISH);
                if (msg != null && !msg.isBlank()) return msg;
            } catch (Exception ignored) {}
        }
        return defaultMsg;
    }

    private String sanitizeFilename(String text) {
        if (text == null || text.isBlank()) return "document";
        String unaccented = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        return unaccented.isBlank() ? "document" : unaccented;
    }
}
