package com.fenixcore.optibienestar360.core.util;

import java.util.Set;
import org.springframework.data.domain.Sort;

/**
 * Validates that every property referenced by a Spring Data {@link Sort}
 * belongs to an explicit allow-list, blocking a client from sorting by (and
 * thereby probing the existence/shape of) columns or associations that
 * aren't exposed as a table column — analogous to {@link RsqlFieldValidator}
 * for the {@code ?filter=} parameter.
 *
 * <p>Pattern shared across services that accept {@code ?sort=} parameters.
 * The allow-list and the error code stay with the calling service so error
 * messages are scoped to its domain.</p>
 */
public final class SortFieldValidator {

	private SortFieldValidator() {}

	/**
	 * @throws IllegalArgumentException with {@code errorCode} as the message
	 *         when any sorted property is outside {@code allowedFields}. The
	 *         exception message is a localization code (resolved by
	 *         {@code GlobalExceptionHandler} into a 422 {@code ProblemDetail}),
	 *         same mechanism as {@link RsqlFieldValidator#validate}.
	 */
	public static void validate(Sort sort, Set<String> allowedFields, String errorCode) {
		if (sort == null || sort.isUnsorted()) {
			return;
		}
		for (Sort.Order order : sort) {
			if (!allowedFields.contains(order.getProperty())) {
				throw new IllegalArgumentException(errorCode);
			}
		}
	}

}
