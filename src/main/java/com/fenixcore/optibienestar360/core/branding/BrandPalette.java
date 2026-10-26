package com.fenixcore.optibienestar360.core.branding;

import java.util.List;

/**
 * Document palette of the OptiBienestar 360 brand (backend spec 17 §4, hub
 * spec 11 §2). Not configurable per organization — one product brand; a
 * white-label variant would need a new ADR.
 *
 * <p>Every value is a {@code #RRGGBB} string so it can be dropped as-is into
 * CSS (Thymeleaf templates) or JRXML attributes.</p>
 *
 * @param primary      Azul Profundo — title bars and table headers (white text on top)
 * @param accent       Azul Vital — subtitles and highlighted totals on light backgrounds
 * @param accentOnDark Verde Lima — highlighted totals on Azul Profundo bars
 *                     (Azul Vital on Azul Profundo is only 2.3:1)
 * @param surface      Gris Perla — group headers and zebra rows
 * @param text         Gris Carbón — body text
 * @param textMuted    secondary text (issue date, footer), 4.8:1 on white
 * @param border       table borders
 * @param negative     negative amounts and errors (≥ 4.5:1 on white)
 * @param positive     positive / "approved" text — never {@code #7BC621} as text (2.1:1)
 * @param stripe       the 4-segment brand stripe of the stationery (footer)
 */
public record BrandPalette(
        String primary,
        String accent,
        String accentOnDark,
        String surface,
        String text,
        String textMuted,
        String border,
        String negative,
        String positive,
        List<String> stripe
) {

    public static final String AZUL_PROFUNDO = "#003F84";
    public static final String AZUL_OCEANO = "#0D498B";
    public static final String AZUL_VITAL = "#067DBC";
    public static final String VERDE_BIENESTAR = "#7BC621";
    public static final String VERDE_LIMA = "#8CDD12";
    public static final String NARANJA_ENERGIA = "#FF7605";
    public static final String GRIS_CARBON = "#1B2733";
    public static final String GRIS_PERLA = "#EEF2F6";

    /** The single product palette. */
    public static final BrandPalette DEFAULT = new BrandPalette(
            AZUL_PROFUNDO,
            AZUL_VITAL,
            VERDE_LIMA,
            GRIS_PERLA,
            GRIS_CARBON,
            "#64748B",
            "#E2E8F0",
            "#B42318",
            "#477904",
            List.of(AZUL_VITAL, VERDE_BIENESTAR, VERDE_LIMA, NARANJA_ENERGIA)
    );
}
