package com.fenixcore.optibienestar360.modules.subsidy.dto;

import com.fenixcore.optibienestar360.modules.subsidy.entity.Subsidy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Output DTO for the admin + self-service subsidy surfaces. Flat record; the
 * member and authorizer are surfaced by UUID (+ member name for display) and the
 * per-beneficiary exonerations are nested. Built via {@link #from} inside the
 * service transaction so the LAZY associations resolve without open-session-in-view.
 */
public record SubsidyDto(
        UUID uuid,
        UUID memberUuid,
        String memberName,
        BigDecimal monthlyPercentage,
        BigDecimal inscriptionPercentage,
        Integer maxExoneratedBeneficiaries,
        String reason,
        UUID authorizedByUuid,
        LocalDate validFrom,
        LocalDate validUntil,
        boolean active,
        String status,
        Instant createdAt,
        Instant updatedAt,
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
                member != null ? member.getUuid() : null,
                person != null ? person.getFullName() : null,
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
