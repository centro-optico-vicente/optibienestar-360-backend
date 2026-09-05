package com.fenixcore.optibienestar360.modules.currency.exception;

/**
 * No {@code exchange_rates} row is vigente for a (base, quote, asOf) lookup —
 * typically because the ingestion job (not yet built, Tarea 2.13) hasn't run
 * and no manual rate was entered either.
 *
 * <p>Unchecked by design (mirrors this codebase's other domain exceptions,
 * e.g. {@link java.util.NoSuchElementException} usage elsewhere): per ADR
 * 0015 §7, callers must <b>degrade, never block</b> an existing business flow
 * (approving a payment, paying a bonus/prize) just because a rate is
 * missing — catch this and leave the conversion fields {@code null} rather
 * than letting it propagate and fail the request.</p>
 */
public class NoExchangeRateAvailableException extends RuntimeException {

    public NoExchangeRateAvailableException(String message) {
        super(message);
    }
}
