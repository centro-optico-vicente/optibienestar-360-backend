package com.fenixcore.optibienestar360.core.branding;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default brand assets bundled in the classpath under {@code branding/}
 * (backend spec 17 §3). They are the last link of every resolution chain, so
 * a freshly provisioned environment — no {@code organizations.logo_key}, no
 * R2 — still produces fully branded documents.
 *
 * <p>Bytes are read once and memoized: the files are immutable inside the jar.</p>
 */
public final class BrandAssets {

    public static final String BASE_PATH = "branding/";
    public static final String LOGO_HORIZONTAL = BASE_PATH + "ob360-logo-horizontal.png";
    public static final String LOGO_NEGATIVE = BASE_PATH + "ob360-logo-negativo.png";
    public static final String ISOTYPE = BASE_PATH + "ob360-isotipo.png";

    /** Brand font family, as registered in openhtmltopdf and in the Jasper font extension. */
    public static final String FONT_FAMILY = "Poppins";

    /** CSS stack: brand font, then the DejaVu fallback shipped with {@code jasperreports-fonts}. */
    public static final String FONT_STACK_CSS = "'Poppins', 'DejaVu Sans', sans-serif";

    /** One embeddable TrueType face of a font family. */
    public record BrandFont(String family, String resourcePath, int weight) {
    }

    /** Poppins faces (OFL 1.1, see {@code branding/fonts/OFL.txt}). */
    public static final List<BrandFont> BRAND_FONTS = List.of(
            new BrandFont(FONT_FAMILY, BASE_PATH + "fonts/Poppins-Regular.ttf", 400),
            new BrandFont(FONT_FAMILY, BASE_PATH + "fonts/Poppins-Medium.ttf", 500),
            new BrandFont(FONT_FAMILY, BASE_PATH + "fonts/Poppins-SemiBold.ttf", 600),
            new BrandFont(FONT_FAMILY, BASE_PATH + "fonts/Poppins-Bold.ttf", 700)
    );

    /**
     * DejaVu Sans faces from {@code jasperreports-fonts}: glyph fallback for
     * characters Poppins lacks, and the whole-document font if the Poppins
     * files are ever removed.
     */
    public static final List<BrandFont> FALLBACK_FONTS = List.of(
            new BrandFont("DejaVu Sans", "net/sf/jasperreports/fonts/dejavu/DejaVuSans.ttf", 400),
            new BrandFont("DejaVu Sans", "net/sf/jasperreports/fonts/dejavu/DejaVuSans-Bold.ttf", 700)
    );

    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    private BrandAssets() {
    }

    /** Default horizontal color logo (light backgrounds). */
    public static byte[] logo() {
        return read(LOGO_HORIZONTAL);
    }

    /** Default all-white logo (Azul Profundo backgrounds). */
    public static byte[] logoNegative() {
        return read(LOGO_NEGATIVE);
    }

    /** Default square isotype. */
    public static byte[] isotype() {
        return read(ISOTYPE);
    }

    /** Whether a classpath resource exists — used to skip a missing font instead of failing. */
    public static boolean exists(String resourcePath) {
        return classLoader().getResource(resourcePath) != null;
    }

    /** Opens a classpath resource; the caller closes the stream. */
    public static InputStream open(String resourcePath) {
        InputStream in = classLoader().getResourceAsStream(resourcePath);
        if (in == null) {
            throw new IllegalStateException("Brand asset not found in classpath: " + resourcePath);
        }
        return in;
    }

    private static byte[] read(String resourcePath) {
        return CACHE.computeIfAbsent(resourcePath, path -> {
            try (InputStream in = open(path)) {
                return in.readAllBytes();
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read brand asset " + path, e);
            }
        });
    }

    private static ClassLoader classLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        return cl != null ? cl : BrandAssets.class.getClassLoader();
    }
}
