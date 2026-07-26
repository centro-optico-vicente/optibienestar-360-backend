package com.fenixcore.optibienestar360.modules.corporate.dto;

import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract.PayerMode;

import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for the admin corporate-contract surface (list + detail). Flat
 * record; the plan and contact user are surfaced by UUID (+ plan name for
 * display) rather than nested. Mapped via {@link #from} inside the service
 * transaction so the LAZY {@code plan} / {@code contactUser} associations
 * resolve without an open-session-in-view.
 */
public record CorporateContractDto(
        UUID uuid,
        UUID planUuid,
        String planName,
        String institutionName,
        String institutionTaxId,
        UUID contactUserUuid,
        PayerMode payerMode,
        Integer expectedMemberCount,
        int actualMemberCount,
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static CorporateContractDto from(CorporateContract c) {
        return new CorporateContractDto(
                c.getUuid(),
                c.getPlan() != null ? c.getPlan().getUuid() : null,
                c.getPlan() != null ? c.getPlan().getName() : null,
                c.getInstitutionName(),
                c.getInstitutionTaxId(),
                c.getContactUser() != null ? c.getContactUser().getUuid() : null,
                c.getPayerMode(),
                c.getExpectedMemberCount(),
                c.getActualMemberCount(),
                c.isActive(),
                c.getStatus(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
