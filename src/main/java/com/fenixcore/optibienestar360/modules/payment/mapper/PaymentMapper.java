package com.fenixcore.optibienestar360.modules.payment.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentLine;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface PaymentMapper {

    @Mapping(target = "membership",              source = "membership")
    @Mapping(target = "member",                  source = "membership.member")
    @Mapping(target = "plan",                   source = "membership.plan")
    @Mapping(target = "currency_Code",           source = "currency.code")
    @Mapping(target = "currency",                source = "currency")
    @Mapping(target = "amountConverted",        expression = "java(convertedAmount(payment))")
    @Mapping(target = "convertedCurrency_Code",  expression = "java(convertedCurrencyCode(payment))")
    @Mapping(target = "convertedCurrency",       expression = "java(convertedCurrencyRef(payment))")
    @Mapping(target = "payerUserUuid",        source = "payerUser.uuid")
    @Mapping(target = "reviewedBy",            source = "reviewedBy")
    @Mapping(target = "discountedByUserUuid", source = "discountedBy.uuid")
    @Mapping(target = "supportFileAvailable", source = "supportFileUrl", qualifiedByName = "isPresent")
    @Mapping(target = "paymentMethod",         expression = "java(paymentMethodCode(payment))")
    @Mapping(target = "referenceNumber",       expression = "java(referenceNumber(payment))")
    PaymentDto toDto(Payment payment);

    @Named("isPresent")
    static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * V117 moved method/reference down to {@code payment_lines} — reads the
     * (today, always single) first line. {@code null}-safe for a payment
     * whose line hasn't been persisted yet (shouldn't happen post-register,
     * but mapping must never throw).
     */
    private static Optional<PaymentLine> firstLine(Payment payment) {
        return payment.getLines().stream().findFirst();
    }

    default String paymentMethodCode(Payment payment) {
        return firstLine(payment).map(l -> l.getPaymentType().getCode()).orElse(null);
    }

    default String referenceNumber(Payment payment) {
        return firstLine(payment).map(PaymentLine::getReferenceNumber).orElse(null);
    }

    /**
     * The persisted payout snapshot (ADR 0015 §5/§6 Caso A) — never
     * recomputed against today's rate. {@code null} when the payment never
     * involved a conversion (not yet approved, or approved in the same
     * currency it settles).
     */
    default BigDecimal convertedAmount(Payment payment) {
        if (payment.getExchangeRateUsed() == null) {
            return null;
        }
        int scale = payment.getMembership() != null && payment.getMembership().getCurrency() != null
                ? payment.getMembership().getCurrency().getDecimalPlaces().intValue() : 2;
        return payment.getAmount().multiply(payment.getExchangeRateUsed()).setScale(scale, RoundingMode.HALF_UP);
    }

    default String convertedCurrencyCode(Payment payment) {
        if (payment.getExchangeRateUsed() == null) {
            return null;
        }
        return payment.getMembership() != null && payment.getMembership().getCurrency() != null
                ? payment.getMembership().getCurrency().getCode() : null;
    }

    default DisplayRef convertedCurrencyRef(Payment payment) {
        if (payment.getExchangeRateUsed() == null || payment.getMembership() == null) {
            return null;
        }
        return DisplayRefs.ref(payment.getMembership().getCurrency());
    }
}
