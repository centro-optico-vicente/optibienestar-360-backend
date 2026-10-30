package com.fenixcore.optibienestar360.modules.payment.dto;

import java.util.UUID;

/**
 * What a payer sees when looking up the membership they want to pay for (V201): enough to confirm it is the
 * right person and plan, nothing more. The holder's name is abbreviated ("María P.").
 */
public record MembershipLookupDto(UUID membershipUuid, String holderName, String planName) {}
