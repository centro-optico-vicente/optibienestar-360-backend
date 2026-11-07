package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Ally request detail ({@code GET /v1/admin/ally-requests/{uuid}}): what the reviewer needs to approve it. */
public record AllyRequestDetailDto(
        UUID uuid,
        Integer allyNumber,
        String name,
        @Display(value = Display.Kind.ENUM, enumScope = "ally_type.segment") Segment segment,
        List<String> allyTypeNames,
        List<String> professionNames,
        @Display(value = Display.Kind.ENUM, enumScope = "ally.registration_status") Ally.RegistrationStatus registrationStatus,
        @Display(value = Display.Kind.ENUM, enumScope = "ally.registration_channel") Ally.RegistrationChannel registrationChannel,
        String taxDocumentType,
        String taxDocumentNumber,
        String contactName,
        String contactEmail,
        String contactPhone,
        Holder holder,
        String address,
        @Display DisplayRef city,
        @Display DisplayRef promoter,
        String registrationNote,
        boolean published,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        @Display(Display.Kind.DATETIME) Instant approvedAt,
        @Display DisplayRef approvedBy,
        @Display(Display.Kind.DATETIME) Instant rejectedAt,
        @Display DisplayRef rejectedBy,
        String rejectionReason,
        boolean hasUser,
        boolean hasActiveAgreement,
        List<Agreement> activeAgreements,
        List<PortalUser> users
) {

    /** The ally's holder person (B2P professional / B2B signing representative). */
    public record Holder(String firstName, String lastName, String documentType, String documentNumber,
                         String email, String phone) {}

    /** An ACTIVE agreement; {@code hasSupport} when the signed copy was uploaded. */
    public record Agreement(
            UUID uuid,
            @Display(Display.Kind.ENUM) AllyAgreement.AgreementType agreementType,
            @Display(Display.Kind.DATE) LocalDate startDate,
            @Display(Display.Kind.DATE) LocalDate endDate,
            boolean hasSupport) {}

    /** A portal login linked to the ally. */
    public record PortalUser(UUID uuid, String email, String status, List<String> roleNames) {}
}
