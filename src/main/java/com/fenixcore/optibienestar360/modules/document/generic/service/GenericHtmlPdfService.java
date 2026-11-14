package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fenixcore.optibienestar360.core.branding.BrandingContext;
import com.fenixcore.optibienestar360.core.branding.BrandingService;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import com.openhtmltopdf.extend.FSCacheEx;
import com.openhtmltopdf.extend.FSCacheValue;
import com.openhtmltopdf.extend.impl.FSDefaultCacheStore;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;

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

    private record FontFace(String file, int weight, FontStyle style) {}

    /** TTF bytes, read from the jar once instead of on every render. */
    private static final List<byte[]> FONT_BYTES = FONT_FACES.stream()
            .map(face -> readFont(FONT_DIR + face.file()))
            .toList();

    /**
     * Font metrics shared across renders. Without it every render parses the TTF tables again to
     * lay text out; the store is a ConcurrentHashMap, so concurrent renders can share it.
     */
    private static final FSCacheEx<String, FSCacheValue> FONT_METRICS_CACHE = new FSDefaultCacheStore();

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

            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                PdfRendererBuilder builder = new PdfRendererBuilder();
                builder.useFastMode();
                builder.useCacheStore(PdfRendererBuilder.CacheStore.PDF_FONT_METRICS, FONT_METRICS_CACHE);
                registerBundledFonts(builder);
                builder.withHtmlContent(renderedHtml, null);
                builder.toStream(out);
                builder.run();

                return out.toByteArray();
            }
        } catch (Exception e) {
            throw new RuntimeException("Error rendering generic PDF document via HTML/openhtmltopdf: " + e.getMessage(), e);
        }
    }

    private static void registerBundledFonts(PdfRendererBuilder builder) {
        for (int i = 0; i < FONT_FACES.size(); i++) {
            FontFace face = FONT_FACES.get(i);
            byte[] bytes = FONT_BYTES.get(i);
            builder.useFont(() -> new ByteArrayInputStream(bytes),
                    FONT_FAMILY, face.weight(), face.style(), true);
        }
    }

    private static byte[] readFont(String path) {
        try (InputStream in = GenericHtmlPdfService.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Bundled font missing from the classpath: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read bundled font " + path, e);
        }
    }
}
