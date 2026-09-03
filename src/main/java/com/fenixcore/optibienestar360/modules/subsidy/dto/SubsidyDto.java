package com.fenixcore.optibienestar360.modules.subsidy.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Output DTO for the admin + self-service subsidy surfaces. Flat record; the
 * member and authorizer are surfaced by UUID (+ member name for display) and the
 * per-beneficiary exonerations are nested. Scalars carry a localized
 * {@code _Display} sibling (hub ADR 0014). Built via {@link #from} inside the
 * service transaction so the LAZY associations resolve without open-session-in-view.
 */
public record SubsidyDto(
        UUID uuid,
        @Display DisplayRef member,
        @Display(Display.Kind.NUMBER) BigDecimal monthlyPercentage,
        @Display(Display.Kind.NUMBER) BigDecimal inscriptionPercentage,
        Integer maxExoneratedBeneficiaries,
        String reason,
        UUID authorizedByUuid,
        @Display(Display.Kind.DATE) LocalDate validFrom,
        @Display(Display.Kind.DATE) LocalDate validUntil,
        @Display(Display.Kind.BOOLEAN) boolean active,
        @Display(value = Display.Kind.ENUM, enumScope = "subsidy.status") String status,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant updatedAt,
        List<SubsidyBeneficiaryDto> beneficiaries
) {
    public static SubsidyDto from(Subsidy s) {
        var member = s.getMember();
        var person = member != null ? member.getPerson() : null;
        List<SubsidyBeneficiaryDto> lines = s.getBeneficiaries() == null ? List.of()
                : s.getBeneficiaries().stream()
                        .filter(sb -> sb.isActive())
                        .map(SubsidyBeneficiaryDto::from)
                        .toList();
        return new SubsidyDto(
                s.getUuid(),
                DisplayRef.of(
                        member != null ? member.getUuid() : null,
                        person != null ? person.getTaxDocumentNumber() : null,
                        person != null ? person.getFullName() : null),
                s.getMonthlyPercentage(),
                s.getInscriptionPercentage(),
                s.getMaxExoneratedBeneficiaries(),
                s.getReason(),
                s.getAuthorizedBy() != null ? s.getAuthorizedBy().getUuid() : null,
                s.getValidFrom(),
                s.getValidUntil(),
                s.isActive(),
                s.getStatus(),
                s.getCreatedAt(),
                s.getUpdatedAt(),
                lines);
    }
}
