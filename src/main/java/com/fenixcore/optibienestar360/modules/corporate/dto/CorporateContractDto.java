package com.fenixcore.optibienestar360.modules.corporate.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract;
import com.fenixcore.optibienestar360.modules.corporate.entity.CorporateContract.PayerMode;

import java.time.Instant;
import java.util.UUID;

/**
 * Output DTO for the admin corporate-contract surface (list + detail). Flat
 * record; the plan and contact user are surfaced by UUID (+ plan name for
 * display). Scalars carry a localized {@code _Display} sibling (hub ADR 0014).
 * Mapped via {@link #from} inside the service transaction so the LAZY
 * {@code plan} / {@code contactUser} associations resolve.
 */
public record CorporateContractDto(
        UUID uuid,
        @Display DisplayRef plan,
        String institutionName,
        String institutionTaxId,
        UUID contactUserUuid,
        @Display(Display.Kind.ENUM) PayerMode payerMode,
        Integer expectedMemberCount,
        int actualMemberCount,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "corporate_contract.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt
) {
    public static CorporateContractDto from(CorporateContract c) {
        return new CorporateContractDto(
                c.getUuid(),
                DisplayRefs.ref(c.getPlan()),
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
