package com.fenixcore.optibienestar360.modules.payment.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface PaymentMapper {

    @Mapping(target = "membershipUuid",         source = "membership.uuid")
    @Mapping(target = "memberUuid",             source = "membership.member.uuid")
    @Mapping(target = "plan",                   source = "membership.plan")
    @Mapping(target = "currency_Code",           source = "currency.code")
    @Mapping(target = "amountConverted",        expression = "java(convertedAmount(payment))")
    @Mapping(target = "convertedCurrency_Code",  expression = "java(convertedCurrencyCode(payment))")
    @Mapping(target = "payerUserUuid",        source = "payerUser.uuid")
    @Mapping(target = "reviewedByUserUuid",   source = "reviewedBy.uuid")
    @Mapping(target = "discountedByUserUuid", source = "discountedBy.uuid")
    @Mapping(target = "supportFileAvailable", source = "supportFileUrl", qualifiedByName = "isPresent")
    PaymentDto toDto(Payment payment);

    @Named("isPresent")
    static boolean isPresent(String value) {
        return value != null && !value.isBlank();
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
}
