package com.fenixcore.optibienestar360.modules.exchangerate.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.currency.entity.Currency;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Historical exchange-rate ledger (V85, ADR 0015 §2) — a time series, not a
 * mutable single value. "The current rate" for a pair as of an instant is
 * always the latest {@link #validFrom} {@code <=} that instant; reads never
 * re-derive the BCV vigency calendar (that complexity is resolved once, at
 * ingestion, by the not-yet-built {@code FetchExchangeRatesJob}).
 *
 * <p>{@link #baseCurrency}/{@link #quoteCurrency} use standard FX terminology
 * (e.g. USD/VES: USD is base, VES is quote) — {@link #rate} is units of quote
 * per 1 unit of base.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "exchange_rates")
@AttributeOverride(name = "id", column = @Column(name = "exchange_rates_id", nullable = false, updatable = false))
public class ExchangeRate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "base_currency_id", nullable = false)
    private Currency baseCurrency;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quote_currency_id", nullable = false)
    private Currency quoteCurrency;

    @Column(precision = 18, scale = 8, nullable = false)
    private BigDecimal rate;

    @Column(name = "operation_date", nullable = false)
    private LocalDate operationDate;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Enumerated(EnumType.STRING)
    @Column(length = 30, nullable = false)
    private Source source;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt = Instant.now();

    /** Values pinned by the V85 CHECK constraint. */
    public enum Source {
        BCV, EXCHANGE_RATES_API, MANUAL
    }
}
