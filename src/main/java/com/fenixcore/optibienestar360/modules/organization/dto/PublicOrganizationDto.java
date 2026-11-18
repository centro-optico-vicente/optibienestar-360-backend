package com.fenixcore.optibienestar360.modules.organization.dto;

/**
 * Public projection of the organization for {@code GET /v1/public/organization}
 * (no authentication). The identity fields are {@code null} unless the admin
 * ticked "public identity" ({@code publicIdentityVisible}); {@code contact} is
 * always filled. Never the signatory, registry or commission settings that
 * {@link OrganizationDto} carries for admins.
 *
 * @param name          commercial name (nombre comercial)
 * @param legalName     registered name (razón social)
 * @param taxIdentifier RIF
 */
public record PublicOrganizationDto(
        String name,
        String legalName,
        String taxIdentifier,
        PublicOrganizationContactDto contact
) {}
