package com.fenixcore.optibienestar360.core.exception;

import java.util.Map;

/**
 * A 422 ({@link IllegalArgumentException} family, same as the plain
 * {@code throw new IllegalArgumentException("code")} pattern) that also
 * carries message arguments and extra ProblemDetail properties — for when the
 * client needs to know <i>which</i> values were rejected, not only why.
 *
 * <p>{@link #getMessage()} is the message code; {@code args} feed its
 * {@code {0}}, {@code {1}}… placeholders and every {@code properties} entry
 * lands as a top-level property of the problem body
 * ({@link GlobalExceptionHandler}).</p>
 */
public class UnprocessableDetailsException extends IllegalArgumentException {

    private final transient Object[] args;
    private final transient Map<String, Object> properties;

    public UnprocessableDetailsException(String messageCode, Object[] args, Map<String, Object> properties) {
        super(messageCode);
        this.args = args != null ? args.clone() : new Object[0];
        this.properties = properties != null ? Map.copyOf(properties) : Map.of();
    }

    public Object[] getArgs() {
        return args.clone();
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
