package com.fenixcore.optibienestar360.core.util;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Transient;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Translates the properties referenced by a Spring Data {@link Sort} into
 * their real JPA paths against a per-entity sortable-field map.
 *
 * <p>The sortable map has two parts:</p>
 * <ul>
 *   <li><b>Scalar columns</b> — every non-association field the entity
 *   declares on itself or inherits from a {@code @MappedSuperclass} (e.g.
 *   {@code createdAt}/{@code updatedAt} from {@code BaseEntity}). These are
 *   derived automatically via {@link #sortableFieldsOf}, so adding a plain
 *   {@code @Column} to an entity makes it sortable with zero backend
 *   changes.</li>
 *   <li><b>Relation "display" columns</b> — a list-item DTO field like
 *   {@code cityName} that's actually {@code city.name} under a
 *   {@code @ManyToOne}. These can't be inferred safely (which field of the
 *   related entity to show is a conscious choice), so the caller supplies
 *   them explicitly as {@code relationAliases}.</li>
 * </ul>
 *
 * <p>{@link #resolve} is intentionally tolerant, not a security whitelist:
 * the only caller of a {@code ?sort=} field is the admin table itself,
 * clicking on the columns it renders, so a field it doesn't recognize is
 * either a stale request or a frontend/backend drift to notice in the logs
 * — not something to reject the whole page load for.</p>
 *
 * <p>Every {@code String}-typed field sorts case-insensitively (Spring
 * Data's {@link Sort.Order#ignoreCase()}, which {@code QueryUtils} turns
 * into a {@code LOWER(...)}-wrapped {@code ORDER BY}) — Postgres' default
 * collation is byte-order, so without this every uppercase-starting value
 * sorts before every lowercase one instead of interleaving alphabetically
 * (e.g. "alexander" landing after "Jefferson" instead of next to
 * "Alexander"). Non-{@code String} fields (dates, booleans, enums, numbers)
 * keep their natural ordering.</p>
 */
public final class SortFieldValidator {

	/** A sortable field's real JPA path plus whether it needs case-insensitive ordering. */
	public record SortableField(String jpaPath, boolean caseInsensitive) {}

	private static final Logger log = LoggerFactory.getLogger(SortFieldValidator.class);

	private static final Set<Class<? extends Annotation>> ASSOCIATION_ANNOTATIONS = Set.of(
		ManyToOne.class,
		OneToOne.class,
		OneToMany.class,
		ManyToMany.class,
		ElementCollection.class
	);

	private SortFieldValidator() {}

	/**
	 * Auto-derives the sortable scalar fields of {@code entityClass} — walking
	 * up through every {@code @MappedSuperclass} so inherited fields like
	 * {@code createdAt} are included — by reflection (excluding JPA
	 * associations, {@code @Transient}, and static fields) and merges in
	 * {@code relationAliases} — keyed by the name the client sends (matching
	 * the list-item DTO field), valued by the real JPA property path (dotted
	 * for relations). A scalar field is marked case-insensitive when its
	 * declared type is {@code String}; a relation alias always is — every
	 * current alias points at a {@code name}-shaped display column, and the
	 * caller only supplies aliases for exactly that kind of column (see
	 * class Javadoc).
	 */
	public static Map<String, SortableField> sortableFieldsOf(Class<?> entityClass, Map<String, String> relationAliases) {
		Map<String, SortableField> fields = new LinkedHashMap<>();
		for (Class<?> c = entityClass; c != null && c != Object.class; c = c.getSuperclass()) {
			for (Field f : c.getDeclaredFields()) {
				boolean isAssociation = ASSOCIATION_ANNOTATIONS.stream().anyMatch(f::isAnnotationPresent);
				if (isAssociation || f.isAnnotationPresent(Transient.class) || Modifier.isStatic(f.getModifiers())) {
					continue;
				}
				fields.putIfAbsent(f.getName(), new SortableField(f.getName(), f.getType() == String.class));
			}
		}
		relationAliases.forEach((key, jpaPath) -> fields.put(key, new SortableField(jpaPath, true)));
		return Collections.unmodifiableMap(fields);
	}

	/**
	 * Translates every property in {@code pageable}'s {@link Sort} to its real
	 * JPA path via {@code sortableFields}, silently dropping (and logging a
	 * warning for) any property that isn't in the map instead of failing the
	 * request. {@code entityKey} is only used to identify the entity in the
	 * warning log line.
	 */
	public static Pageable resolve(Pageable pageable, Map<String, SortableField> sortableFields, String entityKey) {
		if (pageable.getSort().isUnsorted()) {
			return pageable;
		}
		List<Sort.Order> mapped = new ArrayList<>();
		for (Sort.Order order : pageable.getSort()) {
			SortableField target = sortableFields.get(order.getProperty());
			if (target == null) {
				log.warn("Ignoring unsupported sort field '{}' for entity '{}'", order.getProperty(), entityKey);
				continue;
			}
			Sort.Order mappedOrder = order.withProperty(target.jpaPath());
			mapped.add(target.caseInsensitive() ? mappedOrder.ignoreCase() : mappedOrder);
		}
		if (mapped.isEmpty()) {
			return pageable.isUnpaged() ? Pageable.unpaged() : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
		}
		Sort resolvedSort = Sort.by(mapped);
		return pageable.isUnpaged()
			? Pageable.unpaged(resolvedSort)
			: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolvedSort)
		;
	}

}
