package com.fenixcore.optibienestar360.core.branding;

import java.util.Base64;

/**
 * Immutable brand snapshot for one generated document (backend spec 17 §2).
 * Resolved once per document by the branding service (next step of spec 17); every document family (Jasper,
 * HTML → PDF) reads its name, logo, palette and footer from here instead of
 * hardcoding them.
 *
 * @param displayName     {@code organizations.name} (e.g. "OptiBienestar 360")
 * @param legalName       {@code organizations.legal_name}, may be {@code null}
 * @param taxId           {@code organizations.tax_identifier} (RIF), may be {@code null};
 *                        documents omit the "RIF: …" line when absent
 * @param logoPng         color logo for light backgrounds — the organization's own
 *                        ({@code logo_key} in R2) or the classpath default; PNG or JPEG bytes
 * @param logoNegativePng all-white logo for Azul Profundo backgrounds (always classpath)
 * @param isotypePng      square isotype (always classpath)
 * @param logoDataUri     {@code data:image/…;base64,…} of {@link #logoPng}, for HTML/PDF
 *                        templates rendered without a base URI
 * @param palette         document palette (spec 17 §4)
 * @param footer          {@code system_configs.report_footer}, or the default built from
 *                        the organization identity
 */
public record BrandingContext(
        String displayName,
        String legalName,
        String taxId,
        byte[] logoPng,
        byte[] logoNegativePng,
        byte[] isotypePng,
        String logoDataUri,
        BrandPalette palette,
        String footer
) {

    /** Builds a context deriving {@link #logoDataUri} from the logo bytes. */
    public static BrandingContext of(String displayName, String legalName, String taxId,
                                     byte[] logoPng, String footer) {
        return new BrandingContext(
                displayName,
                legalName,
                taxId,
                logoPng,
                BrandAssets.logoNegative(),
                BrandAssets.isotype(),
                toDataUri(logoPng),
                BrandPalette.DEFAULT,
                footer
        );
    }

    /** Font stack for CSS — exposed so templates don't hardcode it. */
    public String fontStackCss() {
        return BrandAssets.FONT_STACK_CSS;
    }

    /** {@code "RIF J-…"} or {@code null} when the organization has no tax id. */
    public String taxIdLine() {
        return taxId == null || taxId.isBlank() ? null : "RIF " + taxId.trim();
    }

    static String toDataUri(byte[] image) {
        if (image == null || image.length == 0) {
            return null;
        }
        String mime = isJpeg(image) ? "image/jpeg" : "image/png";
        return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(image);
    }

    /** JPEG magic number ({@code FF D8 FF}); anything else is treated as PNG. */
    static boolean isJpeg(byte[] image) {
        return image != null && image.length >= 3
                && (image[0] & 0xFF) == 0xFF && (image[1] & 0xFF) == 0xD8 && (image[2] & 0xFF) == 0xFF;
    }
}
