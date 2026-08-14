package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

@Service
public class GenericHtmlPdfService {

    private final TemplateEngine templateEngine;

    public GenericHtmlPdfService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * Generates PDF bytes from any model object by processing the specified Thymeleaf HTML template.
     * Uses current thread locale from LocaleContextHolder.
     */
    public byte[] generatePdf(Object model, String templateName) {
        return generatePdf(model, templateName, LocaleContextHolder.getLocale());
    }

    /**
     * Generates PDF bytes from any model object by processing the specified Thymeleaf HTML template with explicit Locale.
     */
    public byte[] generatePdf(Object model, String templateName, Locale locale) {
        try {
            Locale targetLocale = locale != null ? locale : Locale.ENGLISH;
            Context context = new Context(targetLocale);
            context.setVariable("model", model);

            String finalTemplate = (templateName != null && !templateName.isBlank()) ? templateName : "documents/generic_record_card";
            String renderedHtml = templateEngine.process(finalTemplate, context);

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.withHtmlContent(renderedHtml, null);
                builder.toStream(out);
                builder.run();

                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error rendering generic PDF document via HTML/openhtmltopdf: " + e.getMessage(), e);
        }
    }
}
