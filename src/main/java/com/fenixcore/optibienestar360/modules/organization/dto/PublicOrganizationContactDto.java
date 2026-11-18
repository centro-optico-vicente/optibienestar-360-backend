package com.fenixcore.optibienestar360.modules.organization.dto;

/**
 * Contact data of the organization, always public:
 * {@code GET /v1/public/organization/contact} and the {@code contact} block of
 * {@code GET /v1/public/organization}.
 */
public record PublicOrganizationContactDto(
        String address,
        String email,
        String phones,
        String whatsapp,
        String instagram,
        String facebook
) {}
