package com.fenixcore.optibienestar360.modules.subsidy.dto;

import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyBeneficiary;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Output line for a per-beneficiary exoneration. Built via {@link #from} inside
 * the service transaction so the LAZY {@code beneficiary.person} resolves.
 */
public record SubsidyBeneficiaryDto(
        UUID uuid,
        UUID beneficiaryUuid,
        String beneficiaryName,
        BigDecimal monthlyPercentage,
        BigDecimal inscriptionPercentage
) {
    public static SubsidyBeneficiaryDto from(SubsidyBeneficiary sb) {
        var beneficiary = sb.getBeneficiary();
        var person = beneficiary != null ? beneficiary.getPerson() : null;
        return new SubsidyBeneficiaryDto(
                sb.getUuid(),
                beneficiary != null ? beneficiary.getUuid() : null,
                person != null ? person.getFullName() : null,
                sb.getMonthlyPercentage(),
                sb.getInscriptionPercentage());
    }
}
