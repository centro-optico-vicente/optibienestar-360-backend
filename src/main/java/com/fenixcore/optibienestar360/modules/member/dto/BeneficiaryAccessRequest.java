package com.fenixcore.optibienestar360.modules.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

/**
 * Payload for {@code POST /v1/me/family/{uuid}/access} (V195). {@code email}
 * is where the activation link goes and becomes the beneficiary's login; it
 * may be omitted when the beneficiary already has an email on file.
 */
public record BeneficiaryAccessRequest(
        @Email @Size(max = 255) String email
) {}
