package com.fenixcore.optisaludplus.core.exception;

/**
 * Base for business exceptions whose user-facing message must be localized.
 *
 * <p>The {@code messageCode} is a stable, dot-separated key (e.g.
 * {@code auth.credentials.invalid}); {@link com.fenixcore.optisaludplus.core.exception.GlobalExceptionHandler}
 * resolves it against Spring's {@code MessageSource} using the current
 * request locale (see {@code I18nConfig}).</p>
 *
 * <p>{@code args} are passed through to {@code MessageSource.getMessage}, so
 * bundle values can reference them with {@code {0}}, {@code {1}}, … placeholders.
 * Today most messages do not use args; the field is kept so a code can grow
 * arguments later without breaking the throw sites.</p>
 *
 * <p>The exception's {@link #getMessage()} returns the {@code messageCode}
 * verbatim, so if for any reason the handler doesn't run (test infrastructure
 * bypassing it, etc.) the code surfaces in logs instead of a wrongly-localized
 * literal.</p>
 */
public abstract class LocalizedBusinessException extends RuntimeException {

    private final String messageCode;
    private final Object[] args;

    protected LocalizedBusinessException(String messageCode, Object... args) {
        super(messageCode);
        this.messageCode = messageCode;
        this.args = args != null ? args.clone() : new Object[0];
    }

    public String getMessageCode() {
        return messageCode;
    }

    public Object[] getArgs() {
        return args.clone();
    }
}
