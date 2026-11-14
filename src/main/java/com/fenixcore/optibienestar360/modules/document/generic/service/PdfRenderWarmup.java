package com.fenixcore.optibienestar360.modules.document.generic.service;

import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericRecordModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Renders one throwaway record card in the background once the app is ready, so the first real
 * PDF a user asks for does not pay the one-off startup cost.
 *
 * <p>Measured in production (2026-11-13, ally card): the first render after a restart took
 * 4487 ms and the next one 751 ms for the same record. The difference is one-off work: loading the
 * openhtmltopdf/PDFBox classes, parsing and caching the Thymeleaf template, PDFBox scanning the
 * container fonts and building its font cache for the base-14 fallbacks, the first ImageIO PNG
 * decode of the logo, and filling the shared font-metrics cache in {@link GenericHtmlPdfService}.
 * This render pays all of that at startup instead of during a user's request.
 *
 * <p>It runs on its own thread so startup is not delayed, bypasses {@link PdfRenderLimiter} (the
 * app is not serving yet, so there is no one to compete with), and a failure is only logged: the
 * worst case is the old behaviour, where the first user render pays the cost.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.documents.pdf-warmup.enabled", havingValue = "true", matchIfMissing = true)
public class PdfRenderWarmup {

    private static final String TEMPLATE = "documents/generic_record_card";

    private final GenericHtmlPdfService htmlPdfService;

    public PdfRenderWarmup(GenericHtmlPdfService htmlPdfService) {
        this.htmlPdfService = htmlPdfService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        Thread.ofVirtual().name("pdf-render-warmup").start(this::warmUp);
    }

    void warmUp() {
        long started = System.nanoTime();
        try {
            byte[] pdf = htmlPdfService.generatePdf(sampleModel(), TEMPLATE, Locale.forLanguageTag("es"));
            log.info("PDF render warm-up finished in {} ms ({} bytes)",
                    (System.nanoTime() - started) / 1_000_000, pdf.length);
        } catch (RuntimeException e) {
            log.warn("PDF render warm-up failed after {} ms; the first user render will pay the startup cost: {}",
                    (System.nanoTime() - started) / 1_000_000, e.getMessage());
        }
    }

    /** Regular and bold text, accents, and a detail table: the same paths a real record card takes. */
    static GenericRecordModel sampleModel() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Nombre", "Ficha de calentamiento");
        fields.put("Dirección", "Av. Principal, Acarigua");
        fields.put("Estado", "Activo");
        GenericRecordModel.DetailSection section = new GenericRecordModel.DetailSection(
                "Detalle",
                List.of("Código", "Descripción"),
                List.of(List.of("WARMUP", "Renderizado de arranque")));
        return new GenericRecordModel("Ficha", null, "WARMUP", "01/01/2026 12:00 a. m.", "System",
                fields, List.of(section));
    }
}
