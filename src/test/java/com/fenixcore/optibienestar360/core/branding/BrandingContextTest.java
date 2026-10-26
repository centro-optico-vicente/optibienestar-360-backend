package com.fenixcore.optibienestar360.core.branding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrandingContextTest {

    @Test
    void defaultAssets_areBundledInClasspath() {
        assertTrue(BrandAssets.logo().length > 0);
        assertTrue(BrandAssets.logoNegative().length > 0);
        assertTrue(BrandAssets.isotype().length > 0);
        BrandAssets.BRAND_FONTS.forEach(f -> assertTrue(BrandAssets.exists(f.resourcePath()), f.resourcePath()));
    }

    @Test
    void logoDataUri_usesPngMimeForPngBytes() {
        BrandingContext ctx = BrandingContext.of("OptiBienestar 360", null, null, BrandAssets.logo(), "footer");

        assertTrue(ctx.logoDataUri().startsWith("data:image/png;base64,"));
    }

    @Test
    void logoDataUri_usesJpegMimeForJpegBytes() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};

        BrandingContext ctx = BrandingContext.of("OptiBienestar 360", null, null, jpeg, "footer");

        assertTrue(ctx.logoDataUri().startsWith("data:image/jpeg;base64,"));
    }

    @Test
    void logoDataUri_isNullWithoutLogo() {
        assertNull(BrandingContext.of("OptiBienestar 360", null, null, null, "footer").logoDataUri());
    }

    @Test
    void taxIdLine_isNullWhenBlank_andPrefixedOtherwise() {
        assertNull(BrandingContext.of("X", null, "  ", null, "f").taxIdLine());
        assertEquals("RIF J-12345678-9", BrandingContext.of("X", null, " J-12345678-9 ", null, "f").taxIdLine());
    }
}
