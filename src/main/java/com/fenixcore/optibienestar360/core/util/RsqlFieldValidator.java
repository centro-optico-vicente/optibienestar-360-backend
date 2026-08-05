package com.fenixcore.optibienestar360.core.util;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validates that an RSQL filter expression only references fields in an
 * explicit allow-list, blocking JPA association traversal injection like
 * {@code userRoles.role.permissions.name==ADMIN} from leaking sensitive
 * relations into a query that should only filter top-level columns.
 *
 * <p>Pattern shared across services that accept {@code ?filter=} parameters
 * (auth, catalog, members, …). The allow-list and the error code stay with
 * the calling service so error messages are scoped to its domain.</p>
 */
public final class RsqlFieldValidator {

    /** Captures the field name in expressions like {@code field==value} or
     * {@code field.sub!=value}. Matched either in full (for allow-list
     * entries naming an exact nested path, e.g. {@code person.firstName})
     * or by its root segment (for allow-list entries naming a top-level
     * column, e.g. {@code email}). */
    private static final Pattern FIELD_EXTRACTOR = Pattern
            .compile("([a-zA-Z][a-zA-Z0-9]*(?:\\.[a-zA-Z][a-zA-Z0-9]*)*)\\s*[=!<>]");

    private RsqlFieldValidator() {}

    /**
     * @throws IllegalArgumentException with {@code errorCode} as the message
     *         when any referenced field is outside {@code allowedFields} —
     *         neither the full dotted path nor its root segment is listed.
     *         The exception message is a localization code (resolved by the
     *         GlobalExceptionHandler); the offending field name survives in
     *         the stack trace for log debugging.
     */
    public static void validate(String filter, Set<String> allowedFields, String errorCode) {
        if (filter == null || filter.isBlank()) {
            return;
        }
        Matcher m = FIELD_EXTRACTOR.matcher(filter);
        while (m.find()) {
            String field = m.group(1);
            String rootField = field.split("\\.")[0];
            if (!allowedFields.contains(field) && !allowedFields.contains(rootField)) {
                throw new IllegalArgumentException(errorCode);
            }
        }
    }
}
