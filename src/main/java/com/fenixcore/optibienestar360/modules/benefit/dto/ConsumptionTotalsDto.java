package com.fenixcore.optibienestar360.modules.benefit.dto;

import java.math.BigDecimal;

/**
 * Consumo agregado de una membresía en un período, opcionalmente acotado a un
 * aliado. Es la forma que necesitan tanto los umbrales de fidelidad ("por cada
 * $100 consumidos" / "cada 50 compras", ADR 0013 §5) como el reporte de consumo
 * por rubro.
 *
 * <p>{@code amount} suma solo los usos que traen {@code consumption_amount}:
 * los registrados antes de V158 no lo tienen y únicamente cuentan para
 * {@code count}.</p>
 */
public record ConsumptionTotalsDto(BigDecimal amount, long count) {}
