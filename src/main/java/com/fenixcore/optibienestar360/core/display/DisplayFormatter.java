package com.fenixcore.optibienestar360.core.display;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Single presentation authority for the {@code _Display} convention
 * (hub ADR 0014). Resolves a raw, typed value into the localized,
 * ready-to-render string that travels next to it as a {@code <field>_Display}
 * sibling — dates, money, decimals, enums/status and booleans — plus the
 * label of a foreign-key relation from its already-loaded entity.
 *
 * <p>The formatting rules mirror {@code AuditDisplayResolver} exactly (same
 * zone, same date/number patterns, same {@code ENUM_SHAPED} detection) so a
 * value looks identical in a list, a detail view and an audit snapshot.
 * {@code AuditDisplayResolver} keeps its own resolution registries (JSON key
 * → repository) for now and will delegate its formatting here in a follow-up
 * PR; this pilot introduces the component and wires the Aliados list to it.</p>
 *
 * <p>This component never runs a query — it receives values and
 * already-materialized associations.</p>
 */
@Component
public class DisplayFormatter {

    /** JVM-wide default (ADR 0010 — Venezuela is the only deployment). */
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("America/Caracas");

    private static final DateTimeFormatter DATE_TIME_ES = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter DATE_TIME_EN = DateTimeFormatter.ofPattern("MM-dd-yyyy HH:mm");
    private static final DateTimeFormatter DATE_ONLY_ES = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final DateTimeFormatter DATE_ONLY_EN = DateTimeFormatter.ofPattern("MM-dd-yyyy");

    /** UPPER_SNAKE_CASE enum token, e.g. {@code ACTIVE}, {@code IN_REVIEW}. */
    private static final Pattern ENUM_SHAPED = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

    private static final Currency VES = Currency.getInstance("VES");

    private final MessageSource messageSource;

    public DisplayFormatter(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    // ─── Scalars ───────────────────────────────────────────────────────────

    /** {@code "Sí"}/{@code "No"} (es) · {@code "Yes"}/{@code "No"} (en). {@code null} → {@code null}. */
    public String bool(Boolean value, Locale locale) {
        if (value == null) {
            return null;
        }
        return messageSource.getMessage(
                "display.boolean." + value, null, value ? "Yes" : "No", locale);
    }

    /** {@code dd-MM-yyyy HH:mm} (es) / {@code MM-dd-yyyy HH:mm} (en), zone {@code America/Caracas}. */
    public String dateTime(Instant value, Locale locale) {
        if (value == null) {
            return null;
        }
        DateTimeFormatter pattern = isSpanish(locale) ? DATE_TIME_ES : DATE_TIME_EN;
        return value.atZone(DISPLAY_ZONE).format(pattern);
    }

    /** {@code dd-MM-yyyy} (es) / {@code MM-dd-yyyy} (en). */
    public String date(LocalDate value, Locale locale) {
        if (value == null) {
            return null;
        }
        return value.format(isSpanish(locale) ? DATE_ONLY_ES : DATE_ONLY_EN);
    }

    /** Currency amount in {@code VES} — {@code "Bs. 1.200,00"} (es). */
    public String money(BigDecimal value, Locale locale) {
        if (value == null) {
            return null;
        }
        NumberFormat format = NumberFormat.getCurrencyInstance(locale);
        format.setCurrency(VES);
        return format.format(value);
    }

    /**
     * Locale-aware decimal separators, preserving the value's own scale
     * (whole numbers get no decimals). Mirrors
     * {@code AuditDisplayResolver.formatNumber}.
     */
    public String number(Number value, Locale locale) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            return NumberFormat.getIntegerInstance(locale).format(value.longValue());
        }
        BigDecimal decimal = value instanceof BigDecimal bd ? bd : BigDecimal.valueOf(value.doubleValue());
        if (decimal.stripTrailingZeros().scale() <= 0) {
            return NumberFormat.getIntegerInstance(locale).format(decimal.longValue());
        }
        int scale = Math.max(decimal.scale(), 0);
        NumberFormat format = NumberFormat.getNumberInstance(locale);
        format.setMinimumFractionDigits(scale);
        format.setMaximumFractionDigits(scale);
        return format.format(decimal);
    }

    /**
     * Translates an {@code UPPER_SNAKE_CASE} enum/status token. Lookup order:
     * {@code display.enum.<field>.<value>} → {@code display.enum.common.<value>}
     * → {@code audit.enum.common.<value>} (the bucket auditoría already
     * populates). Returns {@code null} — no fake label — if none resolves or
     * the value isn't enum-shaped. {@code field} scopes the first lookup
     * (e.g. {@code "ally.status"}).
     */
    public String enumLabel(String field, String value, Locale locale) {
        if (value == null || !ENUM_SHAPED.matcher(value).matches()) {
            return null;
        }
        String scoped = messageSource.getMessage(
                "display.enum." + field + "." + value, null, null, locale);
        if (scoped != null) {
            return scoped;
        }
        String common = messageSource.getMessage(
                "display.enum.common." + value, null, null, locale);
        if (common != null) {
            return common;
        }
        return messageSource.getMessage("audit.enum.common." + value, null, null, locale);
    }

    // ─── Foreign-key labels ────────────────────────────────────────────────

    /**
     * Default FK label: {@code name}, else {@code code}, else {@code null}.
     * Never the UUID as text. {@code code}/{@code name} come from the
     * already-loaded related entity.
     */
    public String label(String code, String name) {
        if (name != null && !name.isBlank()) {
            return name;
        }
        if (code != null && !code.isBlank()) {
            return code;
        }
        return null;
    }

    /**
     * Typed-catalog override: {@code "<code> - <name>"} (ADR 0014 §2), the
     * same shape {@code AuditDisplayResolver} emits for {@code allyType} and
     * friends. Falls back to {@link #label} when {@code code} is absent.
     */
    public String catalogLabel(String code, String name) {
        if (code != null && !code.isBlank() && name != null && !name.isBlank()) {
            return code + " - " + name;
        }
        return label(code, name);
    }

    private static boolean isSpanish(Locale locale) {
        return locale != null && "es".equals(locale.getLanguage());
    }
}
