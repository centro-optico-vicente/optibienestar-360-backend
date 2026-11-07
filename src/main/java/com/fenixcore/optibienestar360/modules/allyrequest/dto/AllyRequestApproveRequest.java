package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Approval of an ally request (hub ADR 0036). Approving needs an ACTIVE
 * agreement: the ally already has one, or {@code agreement} registers it now.
 * {@code publish} also publishes the ally in the directory.
 */
public record AllyRequestApproveRequest(
        @Valid AgreementData agreement,
        Boolean publish,
        @Size(max = 1000) String comment
) {

    /** The signed agreement to register (status ACTIVE). */
    public record AgreementData(
            @NotNull AllyAgreement.AgreementType agreementType,
            @NotNull LocalDate startDate,
            LocalDate endDate,
            Boolean autoRenew,
            @Min(0) Integer noticeDays) {}
}
