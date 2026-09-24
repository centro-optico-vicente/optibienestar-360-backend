package com.fenixcore.optibienestar360.core.util;

import java.util.Map;

/**
 * Safe reads of a {@code ScheduledJob.parameters} JSONB map. Jackson can hand
 * back either an {@link Integer} or a {@link Long} for a JSON number
 * depending on the value's magnitude, so every call site that needs an
 * {@code int} out of that map should go through here instead of casting
 * directly.
 */
public final class ScheduledJobParams {

    private ScheduledJobParams() {
    }

    /** Returns {@code fallback} when the key is absent, null, or not a number. */
    public static int intParam(Map<String, Object> parameters, String key, int fallback) {
        if (parameters == null) return fallback;
        Object value = parameters.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }
}
