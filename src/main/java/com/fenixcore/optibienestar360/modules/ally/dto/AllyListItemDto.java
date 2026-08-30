package com.fenixcore.optibienestar360.modules.ally.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Compact projection for list / directory views. Avoids loading nested
 * collections (specialties, users, services, agreements) — those come from
 * {@link AllyDetailDto} on the per-ally GET.
 *
 * <p>Pilot for the {@code _Display} convention (hub ADR 0014): every foreign
 * key travels as the pair {@code <rel>_Uuid} + {@code <rel>_Display}, and
 * every presentational scalar (boolean / status / timestamp) as the raw,
 * typed value + a localized {@code <field>_Display} sibling resolved
 * server-side by {@code DisplayFormatter} from the request {@code Locale}.
 * The frontend renders the {@code _Display} string directly and keeps the
 * raw value for sorting and logic. {@code _Display} fields are read-only —
 * never sent back in a request.</p>
 */
public record AllyListItemDto(
	UUID uuid,
	String name,

	// ─── Foreign keys — <rel>_Uuid + <rel>_Display pair ────────────────
	UUID allyType_Uuid,
	String allyType_Display,

	UUID city_Uuid,
	String city_Display,

	String taxDocumentType,
	String taxDocumentNumber,

	String logoUrl,
	String phone,

	// ─── Presentational scalars — raw + <field>_Display sibling ────────
	boolean published,
	String published_Display,
	Instant publishedAt,
	String publishedAt_Display,

	boolean active,
	String active_Display,

	String status,
	String status_Display,

	Instant createdAt,
	String createdAt_Display
) {}
