package com.fenixcore.optibienestar360.modules.payment.dto;

import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * A payment method an account accepts (V218). Accounts accept several, so this is a plain list element and not a
 * {@code <rel>_Uuid} / {@code <rel>_Display} pair.
 */
public record AccountMethodDto(UUID uuid, String code, String name) {

    public static AccountMethodDto of(PaymentMethod method) {
        return new AccountMethodDto(method.getUuid(), method.getCode(), method.getName());
    }

    /** The methods in a stable order (by name) so the same account always lists them the same way. */
    public static List<AccountMethodDto> listOf(Collection<PaymentMethod> methods) {
        return methods.stream()
                .sorted(Comparator.comparing(PaymentMethod::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(AccountMethodDto::of)
                .toList();
    }
}
