package com.fenixcore.optibienestar360.modules.exchangerate.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.exchangerate.entity.ExchangeRate.Source;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Output DTO for {@code GET /v1/admin/exchange-rates} (ADR 0015 §2/§7).
 * {@code rate} is units of {@code quoteCurrency_Code} per 1 unit of
 * {@code baseCurrency_Code} (e.g. base {@code USD}, quote {@code VES},
 * {@code rate = 805.42}).
 */
public record ExchangeRateDto(
        UUID uuid,
        String baseCurrency_Code,
        String quoteCurrency_Code,
        @Display(Display.Kind.NUMBER) BigDecimal rate,
        @Display(Display.Kind.DATE) LocalDate operationDate,
        @Display(Display.Kind.DATETIME) Instant validFrom,
        @Display(value = Display.Kind.ENUM, enumScope = "exchange_rate.source") Source source,
        @Display(Display.Kind.DATETIME) Instant fetchedAt,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(Display.Kind.DATETIME) Instant createdAt
) {}
