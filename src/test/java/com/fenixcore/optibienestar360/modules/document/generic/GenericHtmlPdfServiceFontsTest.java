package com.fenixcore.optibienestar360.modules.document.generic;

import com.fenixcore.optibienestar360.modules.document.generic.dto.GenericRecordModel;
import com.fenixcore.optibienestar360.modules.document.generic.service.GenericHtmlPdfService;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The generic PDFs must embed the bundled Liberation Sans faces instead of falling back to the
 * base-14 fonts, which made pdfbox scan the container's system fonts on the first render.
 */
class GenericHtmlPdfServiceFontsTest {

    @Test
    void recordCardEmbedsBundledFontAndNoBase14Font() throws Exception {
        byte[] pdf = newService().generatePdf(sampleModel(), "documents/generic_record_card");

        assertBundledFontsOnly(fontNames(pdf));
    }

    /**
     * Font bytes and font metrics are cached across renders (static, shared by every service
     * instance): a render served from those caches must still embed the bundled faces.
     */
    @Test
    void repeatedRendersKeepEmbeddingBundledFont() throws Exception {
        GenericHtmlPdfService service = newService();

        byte[] first = service.generatePdf(sampleModel(), "documents/generic_record_card");
        byte[] second = service.generatePdf(sampleModel(), "documents/generic_record_card");

        assertBundledFontsOnly(fontNames(first));
        assertBundledFontsOnly(fontNames(second));
        assertEquals(fontNames(first), fontNames(second), "both renders embed the same faces");
    }

    private static GenericHtmlPdfService newService() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine templateEngine = new SpringTemplateEngine();
        templateEngine.setTemplateResolver(resolver);
        return new GenericHtmlPdfService(templateEngine, null);
    }

    private static GenericRecordModel sampleModel() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Nombre", "Clínica Central");
        fields.put("Teléfono", "+58 212 555 0101");
        return new GenericRecordModel(
                "Ficha de Aliado", null, "ALY-1", "11/11/2026 09:00 PM", "tester", fields, List.of());
    }

    private static List<String> fontNames(byte[] pdf) throws Exception {
        List<String> fontNames = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            for (PDPage page : document.getPages()) {
                PDResources resources = page.getResources();
                for (COSName name : resources.getFontNames()) {
                    fontNames.add(resources.getFont(name).getName());
                }
            }
        }
        return fontNames;
    }

    private static void assertBundledFontsOnly(List<String> fontNames) {
        assertFalse(fontNames.isEmpty(), "the PDF declares fonts");
        assertTrue(fontNames.stream().anyMatch(n -> n.contains("LiberationSans")),
                "bundled Liberation Sans is embedded, got " + fontNames);
        assertTrue(fontNames.stream().noneMatch(n -> n.startsWith("Helvetica") || n.startsWith("Times")),
                "no base-14 fallback font is used, got " + fontNames);
    }
}
