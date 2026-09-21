package com.fenixcore.optibienestar360.modules.payment.mapper;

import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentLine;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Mapper(componentModel = "spring", uses = DisplayRefs.class)
public interface PaymentMapper {

    @Mapping(target = "direction",                source = "direction")
    @Mapping(target = "paymentType",              source = "paymentType")
    @Mapping(target = "person",                   source = "person")
    @Mapping(target = "promoter",                 source = "promoter")
    @Mapping(target = "membership",              source = "membership")
    @Mapping(target = "member",                  source = "membership.member")
    @Mapping(target = "plan",                   source = "membership.plan")
    @Mapping(target = "campaign",                source = "campaign")
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
	@Mapping(target = "paymentMethodDescription", expression = "java(paymentMethodDescription(payment))")
	@Mapping(target = "paymentMethodMandatoryIdentification", expression = "java(paymentMethodFlag(payment, 5))")
	@Mapping(target = "paymentMethodMandatoryBank", expression = "java(paymentMethodFlag(payment, 6))")
	@Mapping(target = "paymentMethodMandatoryBankAccount", expression = "java(paymentMethodFlag(payment, 1))")
	@Mapping(target = "paymentMethodMandatoryAccountType", expression = "java(paymentMethodFlag(payment, 7))")
	@Mapping(target = "paymentMethodMandatoryAccountCode", expression = "java(paymentMethodFlag(payment, 8))")
	@Mapping(target = "paymentMethodMandatoryPhone", expression = "java(paymentMethodFlag(payment, 2))")
	@Mapping(target = "paymentMethodMandatoryEmail", expression = "java(paymentMethodFlag(payment, 3))")
	@Mapping(target = "paymentMethodMandatoryReferenceNumber", expression = "java(paymentMethodFlag(payment, 4))")
    @Mapping(target = "referenceNumber",       expression = "java(referenceNumber(payment))")
	@Mapping(target = "bank", expression = "java(bankRef(payment))")
	@Mapping(target = "identification", expression = "java(lineValue(payment, 1))")
	@Mapping(target = "bankAccountType", expression = "java(lineValue(payment, 2))")
	@Mapping(target = "bankAccountCode", expression = "java(lineValue(payment, 3))")
	@Mapping(target = "bankAccountIdentifier", expression = "java(lineValue(payment, 4))")
	@Mapping(target = "phone", expression = "java(lineValue(payment, 5))")
	@Mapping(target = "email", expression = "java(lineValue(payment, 6))")
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

	default String paymentMethodDescription(Payment payment) {
		return firstLine(payment).map(l -> l.getPaymentType().getDescription()).orElse(null);
	}

	default boolean paymentMethodFlag(Payment payment, int flag) {
		return firstLine(payment).map(l -> switch (flag) {
			case 1 -> l.getPaymentType().isMandatoryBankAccount();
			case 2 -> l.getPaymentType().isMandatoryPhone();
			case 3 -> l.getPaymentType().isMandatoryEmail();
			case 4 -> l.getPaymentType().isMandatoryReferenceNumber();
			case 5 -> l.getPaymentType().isMandatoryIdentification();
			case 6 -> l.getPaymentType().isMandatoryBank();
			case 7 -> l.getPaymentType().isMandatoryAccountType();
			case 8 -> l.getPaymentType().isMandatoryAccountCode();
			default -> false;
		}).orElse(false);
	}

	/** Line-level bank (V117 FK) — only populated when the method's {@code isMandatoryBank} is true. */
	default DisplayRef bankRef(Payment payment) {
		return firstLine(payment).map(PaymentLine::getBank)
				.map(b -> DisplayRef.of(b.getUuid(), b.getCode(), b.getShortName()))
				.orElse(null);
	}

	default String lineValue(Payment payment, int field) {
		return firstLine(payment).map(l -> switch (field) {
			case 1 -> l.getIdentification();
			case 2 -> l.getBankAccountType();
			case 3 -> l.getBankAccountCode();
			case 4 -> l.getBankAccountIdentifier();
			case 5 -> l.getPhone();
			case 6 -> l.getEmail();
			default -> null;
		}).orElse(null);
	}

    /** {@code promoter_Code} carries the referral code (Promoter has no generic {@code getCode()}) — same as {@code CommissionMapper}. */
    default DisplayRef promoterRef(Promoter promoter) {
        return promoter == null ? null
                : DisplayRef.of(promoter.getUuid(), promoter.getReferralCode(), promoter.getDisplayName());
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
