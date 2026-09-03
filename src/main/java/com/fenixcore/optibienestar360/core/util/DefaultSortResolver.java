package com.fenixcore.optibienestar360.core.util;

import com.fenixcore.optibienestar360.core.audit.EntityConfigService;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Shared 3-layer default-sort fallback for admin list endpoints — extracted
 * from the Ally pilot ({@code AlliesService}) so every other entity reuses
 * the same resolution instead of re-implementing it:
 *
 * <ol>
 *   <li>{@code entity_config}'s own {@code default_sort} for the entity.</li>
 *   <li>{@code system_configs}' global {@code default_sort} fallback.</li>
 *   <li>The caller's hard-coded default (e.g. {@code name ASC} for a
 *       catalog, {@code createdAt DESC} for a transactional entity).</li>
 * </ol>
 *
 * <p>Only kicks in when the request's {@link Pageable} is unsorted — an
 * explicit client {@code ?sort=} always wins and is returned as-is. Field
 * names throughout are client-facing (not translated JPA paths); callers
 * still run the result through their own {@code SortFieldValidator}
 * sortable-fields map before querying, same as an explicit {@code ?sort=}.</p>
 */
@Component
@RequiredArgsConstructor
public class DefaultSortResolver {

	private final EntityConfigService entityConfigService;
	private final SystemConfigService systemConfigService;

	/**
	 * The sort a list endpoint should actually apply for {@code pageable},
	 * in client-facing field names — same value whether it came from an
	 * explicit {@code ?sort=} or the fallback chain. Exposed so the
	 * controller can report it back via {@link AppliedSortPage}.
	 */
	public List<SortOrder> effectiveSort(String entityKey, Pageable pageable, SortOrder hardDefault) {
		if (pageable.getSort().isSorted()) {
			return pageable.getSort().stream()
				.map(o -> new SortOrder(o.getProperty(), o.getDirection().name()))
				.toList();
		}
		List<SortOrder> configured = entityConfigService.getDefaultSort(entityKey);
		if (!configured.isEmpty()) {
			return configured;
		}
		List<SortOrder> global = systemConfigService.getDefaultSort();
		if (!global.isEmpty()) {
			return global;
		}
		return List.of(hardDefault);
	}

	/**
	 * Applies {@link #effectiveSort} to {@code pageable} when it has no
	 * explicit sort yet; returns it untouched otherwise.
	 */
	public Pageable withDefaultSortIfUnsorted(String entityKey, Pageable pageable, SortOrder hardDefault) {
		if (pageable.getSort().isSorted()) {
			return pageable;
		}
		Sort resolved = Sort.by(effectiveSort(entityKey, pageable, hardDefault).stream()
			.map(o -> new Sort.Order(Sort.Direction.fromString(o.direction()), o.field()))
			.toList());
		return pageable.isUnpaged()
			? Pageable.unpaged(resolved)
			: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolved);
	}

	/** {@link #effectiveSort(String, Pageable, SortOrder)} with the project-wide hard default ({@link SortOrder#DEFAULT}). */
	public List<SortOrder> effectiveSort(String entityKey, Pageable pageable) {
		return effectiveSort(entityKey, pageable, SortOrder.DEFAULT);
	}

	/** {@link #withDefaultSortIfUnsorted(String, Pageable, SortOrder)} with the project-wide hard default ({@link SortOrder#DEFAULT}). */
	public Pageable withDefaultSortIfUnsorted(String entityKey, Pageable pageable) {
		return withDefaultSortIfUnsorted(entityKey, pageable, SortOrder.DEFAULT);
	}

}
