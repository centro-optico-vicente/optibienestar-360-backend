package com.fenixcore.optibienestar360.core.util;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * A {@link Page} that also reports the sort actually applied to it, in
 * client-facing field names (the same keys {@code ?sort=} accepts, e.g.
 * {@code "allyType_Display"} — not translated JPA paths like
 * {@code "allyType.name"}).
 *
 * <p>Exists because a request with no explicit {@code ?sort=} still gets
 * ordered — by {@code entity_config}'s configured default, then
 * {@code system_configs}' global fallback, then a hard-coded default — and
 * the admin table has no other way to know which columns (and directions)
 * that default landed on, to reflect it in the header arrows. A plain
 * {@link Page}'s JSON serialization only exposes {@code sorted}/{@code
 * unsorted} booleans, not the actual fields.</p>
 *
 * <p>Subclasses {@link PageImpl} (rather than wrapping/flattening fields by
 * hand) so every existing {@code Page<T>} field a client already reads
 * (`content`, `totalElements`, `pageable`, ...) keeps serializing exactly as
 * before — {@code appliedSort} is a pure addition.</p>
 */
public class AppliedSortPage<T> extends PageImpl<T> {

	private final List<SortOrder> appliedSort;

	public AppliedSortPage(Page<T> page, List<SortOrder> appliedSort) {
		super(page.getContent(), page.getPageable(), page.getTotalElements());
		this.appliedSort = appliedSort;
	}

	public List<SortOrder> getAppliedSort() {
		return appliedSort;
	}

}
