package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fenixcore.optibienestar360.core.branding.BrandingContext;
import com.fenixcore.optibienestar360.core.branding.BrandingService;
import com.fenixcore.optibienestar360.core.exception.ServiceBusyException;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@Service
public class GenericHtmlPdfService {

    /**
     * Font family the generic templates ask for. Bundled in the jar (Liberation Sans, SIL OFL 1.1,
     * metric-compatible with Helvetica/Arial so layouts do not shift) and registered on every render,
     * so pdfbox never falls back to the base-14 fonts and never scans the container's system fonts
     * (that scan rebuilt its font cache on the first PDF after each start and spiked native memory).
     */
    static final String FONT_FAMILY = "Liberation Sans";
    private static final String FONT_DIR = "/fonts/liberation/";
    private static final List<FontFace> FONT_FACES = List.of(
            new FontFace("LiberationSans-Regular.ttf", 400, FontStyle.NORMAL),
            new FontFace("LiberationSans-Bold.ttf", 700, FontStyle.NORMAL),
            new FontFace("LiberationSans-Italic.ttf", 400, FontStyle.ITALIC),
            new FontFace("LiberationSans-BoldItalic.ttf", 700, FontStyle.ITALIC)
    );

    /**
     * At most this many PDFs render at once: each render holds the parsed fonts and the whole
     * document in memory, and several in parallel multiply that peak inside the container limit.
     */
    static final int MAX_CONCURRENT_RENDERS = 2;
    private static final long RENDER_WAIT_SECONDS = 30;
    private static final int RETRY_AFTER_SECONDS = 10;
    private final Semaphore renderPermits = new Semaphore(MAX_CONCURRENT_RENDERS, true);

    private record FontFace(String file, int weight, FontStyle style) {}

    private final TemplateEngine templateEngine;
    private final SystemConfigService systemConfigService;
    private final BrandingService brandingService;

    @Autowired

    public GenericHtmlPdfService(
            TemplateEngine templateEngine,
            SystemConfigService systemConfigService,
            ObjectProvider<BrandingService> brandingServiceProvider
    ) {
        this.templateEngine = templateEngine;
        this.systemConfigService = systemConfigService;
        this.brandingService = brandingServiceProvider != null ? brandingServiceProvider.getIfAvailable() : null;
    }

    public GenericHtmlPdfService(TemplateEngine templateEngine, SystemConfigService systemConfigService) {
        this(templateEngine, systemConfigService, (ObjectProvider<BrandingService>) null);
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

            BrandingContext branding = brandingService != null ? brandingService.getContext() : null;
            context.setVariable("branding", branding);

            String reportFooter = branding != null
                    ? branding.footer()
                    : (systemConfigService != null ? systemConfigService.getReportFooter() : null);
            context.setVariable("reportFooter", reportFooter);

            String finalTemplate = (templateName != null && !templateName.isBlank()) ? templateName : "documents/generic_record_card";
            String renderedHtml = templateEngine.process(finalTemplate, context);

            acquireRenderPermit();
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                registerBundledFonts(builder);
                builder.withHtmlContent(renderedHtml, null);
                builder.toStream(out);
                builder.run();

                return out.toByteArray();
            } finally {
                renderPermits.release();
            }
        } catch (ServiceBusyException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error rendering generic PDF document via HTML/openhtmltopdf: " + e.getMessage(), e);
        }
    }

    private void acquireRenderPermit() {
        boolean acquired;
        try {
            acquired = renderPermits.tryAcquire(RENDER_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceBusyException("document.error.busy", RETRY_AFTER_SECONDS);
        }
        if (!acquired) {
            throw new ServiceBusyException("document.error.busy", RETRY_AFTER_SECONDS);
        }
    }

    private static void registerBundledFonts(PdfRendererBuilder builder) {
        for (FontFace face : FONT_FACES) {
            String path = FONT_DIR + face.file();
            builder.useFont(() -> GenericHtmlPdfService.class.getResourceAsStream(path),
                    FONT_FAMILY, face.weight(), face.style(), true);
        }
    }
}
