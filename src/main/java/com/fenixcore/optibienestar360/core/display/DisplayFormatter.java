package com.fenixcore.optibienestar360.core.display;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Single presentation authority for the {@code _Display} convention
 * (hub ADR 0014). Resolves a raw, typed value into the localized,
 * ready-to-render string that travels next to it as a {@code <field>_Display}
 * sibling — dates, money, decimals, enums/status and booleans — plus the
 * label of a foreign-key relation from its already-loaded entity.
 *
 * <p>Everything that varies by locale — the date/time patterns included — is
 * resolved through {@code MessageSource}, so adding a language is a bundle
 * change, never a code change: number/currency come from
 * {@link NumberFormat} keyed by {@link Locale}, enums/booleans from message
 * keys, and the date patterns from {@code display.format.datetime} /
 * {@code display.format.date} (parsed once, then cached). The display zone is
 * the {@code TZ} environment variable, falling back to {@code America/Caracas}
 * (ADR 0010) when {@code TZ} is unset or invalid.</p>
 *
 * <p>Output matches {@code AuditDisplayResolver} for the {@code es}/{@code en}
 * bundles shipped today; that resolver keeps its own resolution registries
 * (JSON key → repository) for now and will delegate its formatting here in a
 * follow-up PR. This pilot introduces the component and wires the Aliados
 * list to it.</p>
 *
 * <p>This component never runs a query — it receives values and
 * already-materialized associations.</p>
 */
@Slf4j
@Component
public class DisplayFormatter {

	/** Fallback zone when {@code TZ} is unset or invalid — ADR 0010. */
	private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Caracas");

	/** Resolved once: {@code TZ} env var if valid, else {@link #DEFAULT_ZONE}. */
	private static final ZoneId DISPLAY_ZONE = resolveDisplayZone();

	private static final String KEY_DATETIME_PATTERN = "display.format.datetime";
	private static final String KEY_DATE_PATTERN = "display.format.date";
	/** Fallbacks if a bundle omits the pattern key — keep in sync with messages.properties. */
	private static final String DEFAULT_DATETIME_PATTERN = "dd-MM-yyyy HH:mm";
	private static final String DEFAULT_DATE_PATTERN = "dd-MM-yyyy";

	/** UPPER_SNAKE_CASE enum token, e.g. {@code ACTIVE}, {@code IN_REVIEW}. */
	private static final Pattern ENUM_SHAPED = Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$");

	private static final Currency VES = Currency.getInstance("VES");

	private final MessageSource messageSource;

	/** Parsed-pattern cache, keyed by the pattern string (not the locale). */
	private final Map<String, DateTimeFormatter> formatterCache = new ConcurrentHashMap<>();

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

	/** Locale's {@code display.format.datetime} pattern, in zone {@code America/Caracas}. */
	public String dateTime(Instant value, Locale locale) {
		if (value == null) {
			return null;
		}
		return value.atZone(DISPLAY_ZONE)
				.format(formatter(KEY_DATETIME_PATTERN, DEFAULT_DATETIME_PATTERN, locale));
	}

	/** Locale's {@code display.format.date} pattern. */
	public String date(LocalDate value, Locale locale) {
		if (value == null) {
			return null;
		}
		return value.format(formatter(KEY_DATE_PATTERN, DEFAULT_DATE_PATTERN, locale));
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

	/**
	 * Resolves the locale's date pattern from {@code MessageSource} (falling
	 * back to {@code defaultPattern} if the bundle omits the key) and returns
	 * the parsed {@link DateTimeFormatter}, caching by pattern string so each
	 * distinct pattern is compiled once regardless of how many locales map
	 * to it.
	 */
	private DateTimeFormatter formatter(String key, String defaultPattern, Locale locale) {
		String pattern = messageSource.getMessage(key, null, defaultPattern, locale);
		return formatterCache.computeIfAbsent(pattern, DateTimeFormatter::ofPattern);
	}

	/**
	 * Display zone from the {@code TZ} environment variable (e.g.
	 * {@code America/Bogota}); {@link #DEFAULT_ZONE} when {@code TZ} is unset,
	 * blank, or not a valid zone id.
	 */
	private static ZoneId resolveDisplayZone() {
		String tz = System.getenv("TZ");
		if (tz != null && !tz.isBlank()) {
			try {
				return ZoneId.of(tz.trim());
			} catch (DateTimeException invalid) {
				log.warn("Invalid TZ env value '{}', falling back to {}", tz, DEFAULT_ZONE);
			}
		}
		return DEFAULT_ZONE;
	}

}
