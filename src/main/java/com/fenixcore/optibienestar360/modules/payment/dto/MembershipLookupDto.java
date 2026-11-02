package com.fenixcore.optibienestar360.modules.payment.dto;

import java.util.UUID;

/**
 * What a payer sees when confirming who they pay for (V203): enough to recognise the right holder, nothing more.
 * The name is abbreviated ("María P."). {@code membershipUuid}, {@code contractNumber} and {@code planName} are null
 * when the holder has no active membership — the payment then becomes their credit.
 */
public record MembershipLookupDto(
        UUID personUuid,
        UUID membershipUuid,
        String contractNumber,
        String holderName,
        String planName
) {}
