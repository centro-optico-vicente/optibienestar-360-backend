package com.fenixcore.optibienestar360.modules.subsidy.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.modules.subsidy.entity.SubsidyBeneficiary;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Output line for a per-beneficiary exoneration. Built via {@link #from} inside
 * the service transaction so the LAZY {@code beneficiary.person} resolves.
 * The percentages carry a localized {@code _Display} sibling (hub ADR 0014).
 */
public record SubsidyBeneficiaryDto(
        UUID uuid,
        UUID beneficiaryUuid,
        String beneficiaryName,
        @Display(Display.Kind.NUMBER) BigDecimal monthlyPercentage,
        @Display(Display.Kind.NUMBER) BigDecimal inscriptionPercentage
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
