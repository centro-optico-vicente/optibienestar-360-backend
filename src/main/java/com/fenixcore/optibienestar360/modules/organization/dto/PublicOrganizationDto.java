package com.fenixcore.optibienestar360.modules.organization.dto;

/**
 * Public projection of the organization for {@code GET /v1/public/organization}
 * (no authentication): who the company is and how to reach it. Only data meant
 * for the website — never the signatory, registry or commission settings that
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
        String address,
        String email,
        String phones,
        String whatsapp,
        String instagram,
        String facebook
) {}
