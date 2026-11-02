package com.fenixcore.optibienestar360.modules.member.event;

/**
 * A holder granted portal access to a beneficiary (V195): the beneficiary gets
 * an email with the link to create their password. Published inside the grant
 * transaction, sent only after it commits.
 */
public record BeneficiaryAccessInvitedEvent(
        String email,
        String beneficiaryName,
        String holderName,
        String activationUrl
) {}
