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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Resolves and validates the properties referenced by a Spring Data
 * {@link Sort} against a per-entity sortable-field map, blocking a client
 * from ordering by (and thereby probing the existence/shape of) a column or
 * association that isn't deliberately exposed — analogous to
 * {@link RsqlFieldValidator} for the {@code ?filter=} parameter.
 *
 * <p>The sortable map has two parts:</p>
 * <ul>
 *   <li><b>Scalar columns</b> — every non-association field the entity
 *   declares on itself. These are derived automatically via
 *   {@link #sortableFieldsOf}, so adding a plain {@code @Column} to an
 *   entity makes it sortable with zero backend changes.</li>
 *   <li><b>Relation "display" columns</b> — a list-item DTO field like
 *   {@code cityName} that's actually {@code city.name} under a
 *   {@code @ManyToOne}. These can't be inferred safely (which field of the
 *   related entity to show is a conscious choice, same as
 *   {@code RsqlFieldValidator}'s allow-list is for filter paths), so the
 *   caller supplies them explicitly as {@code relationAliases}.</li>
 * </ul>
 */
public final class SortFieldValidator {

	private static final Set<Class<? extends Annotation>> ASSOCIATION_ANNOTATIONS = Set.of(
		ManyToOne.class,
		OneToOne.class,
		OneToMany.class,
		ManyToMany.class,
		ElementCollection.class
	);

	private SortFieldValidator() {}

	/**
	 * Auto-derives the sortable scalar fields of {@code entityClass} by
	 * reflection (excluding JPA associations, {@code @Transient}, and static
	 * fields) and merges in {@code relationAliases} — keyed by the name the
	 * client sends (matching the list-item DTO field), valued by the real
	 * JPA property path (dotted for relations).
	 */
	public static Map<String, String> sortableFieldsOf(Class<?> entityClass, Map<String, String> relationAliases) {
		Map<String, String> fields = new LinkedHashMap<>();
		for (Field f : entityClass.getDeclaredFields()) {
			boolean isAssociation = ASSOCIATION_ANNOTATIONS.stream().anyMatch(f::isAnnotationPresent);
			if (isAssociation || f.isAnnotationPresent(Transient.class) || Modifier.isStatic(f.getModifiers())) {
				continue;
			}
			fields.put(f.getName(), f.getName());
		}
		fields.putAll(relationAliases);
		return Collections.unmodifiableMap(fields);
	}

	/**
	 * Validates every property in {@code pageable}'s {@link Sort} against
	 * {@code sortableFields} and returns an equivalent {@link Pageable} with
	 * each property translated to its real JPA path.
	 *
	 * @throws IllegalArgumentException with {@code errorCode} as the message
	 *         when any sorted property is outside {@code sortableFields}. The
	 *         exception message is a localization code (resolved by
	 *         {@code GlobalExceptionHandler} into a 422 {@code ProblemDetail}),
	 *         same mechanism as {@link RsqlFieldValidator#validate}.
	 */
	public static Pageable resolve(Pageable pageable, Map<String, String> sortableFields, String errorCode) {
		if (pageable.getSort().isUnsorted()) {
			return pageable;
		}
		List<Sort.Order> mapped = new ArrayList<>();
		for (Sort.Order order : pageable.getSort()) {
			String jpaPath = sortableFields.get(order.getProperty());
			if (jpaPath == null) {
				throw new IllegalArgumentException(errorCode);
			}
			mapped.add(order.withProperty(jpaPath));
		}
		Sort resolvedSort = Sort.by(mapped);
		return pageable.isUnpaged()
			? Pageable.unpaged(resolvedSort)
			: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), resolvedSort)
		;
	}

}
