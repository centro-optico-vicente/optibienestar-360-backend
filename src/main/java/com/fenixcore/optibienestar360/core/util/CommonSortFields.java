package com.fenixcore.optibienestar360.core.util;

import java.util.Set;

/**
 * Columns present on (almost) every entity, via {@code BaseEntity} /
 * {@code BaseAuditEntity} — {@code id}, {@code uuid}, {@code createdAt},
 * {@code updatedAt}, {@code createdBy}, {@code updatedBy}. Deliberately
 * excludes {@code active}/{@code status}: not every entity has them (e.g.
 * {@code EntityConfig} itself, or anything extending {@code BaseAuditEntity}
 * lacks {@code status}), so allowing them here would be a false promise for
 * some entities.
 *
 * <p>Used to whitelist {@code system_configs.default_sort} — the global
 * fallback sort applied to any entity without its own configured default —
 * since that setting isn't checked against any single entity's actual
 * sortable-fields map the way {@code EntityConfig.defaultSort} is (it flows
 * through {@code SortFieldValidator.resolve} downstream, per entity).</p>
 */
public final class CommonSortFields {

	public static final Set<String> COMMON_SORTABLE_FIELDS = Set.of(
		"id", "uuid", "createdAt", "updatedAt", "createdBy", "updatedBy"
	);

	private CommonSortFields() {}

}
