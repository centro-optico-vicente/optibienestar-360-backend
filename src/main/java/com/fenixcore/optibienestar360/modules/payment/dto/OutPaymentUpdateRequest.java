package com.fenixcore.optibienestar360.modules.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OutPaymentUpdateRequest(
	@NotNull UUID paymentCategoryUuid,
	@NotNull UUID promoterUuid,
	@NotNull UUID personUuid,
	@NotNull @Digits(integer = 8, fraction = 2)
	@DecimalMin(value = "0.01", message = "{payment.amount.positive}") BigDecimal amount,
	@Pattern(regexp = "^[A-Z]{3,4}$", message = "{payment.currency.iso}") String currency,
	@NotNull UUID paymentMethodUuid,
	UUID bankUuid,
	@Size(max = 80) String identification,
	@Size(max = 40) String bankAccountType,
	@Size(max = 40) String bankAccountCode,
	@Size(max = 120) String bankAccountIdentifier,
	@Size(max = 40) String phone,
	@Size(max = 160) String email,
	@Size(max = 80) String referenceNumber,
	@NotNull @PastOrPresent Instant paymentDate,
	String adminNotes
) {}
