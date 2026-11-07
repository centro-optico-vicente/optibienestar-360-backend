package com.fenixcore.optibienestar360.modules.ally.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;

import java.util.List;
import java.util.UUID;

/**
 * The ally's own profile as the portal edits it ({@code GET/PUT
 * /v1/me/allies/{allyUuid}/profile}, hub ADR 0036). Only the fields the ally
 * may change itself, plus the registration status and a completeness meter
 * that tells a pending ally what is still missing. Never carries the RIF,
 * the holder's identity or anything staff-only.
 */
public record MyAllyProfileDto(
        UUID uuid,
        String name,
        /** The name may only change while the request is PENDING (it goes into the agreement). */
        boolean nameEditable,
        @Display(value = Display.Kind.ENUM, enumScope = "ally_type.segment") Segment segment,
        @Display(value = Display.Kind.ENUM, enumScope = "ally.registration_status") Ally.RegistrationStatus registrationStatus,
        String rejectionReason,
        List<String> allyTypeNames,

        String email,
        String phone,
        String whatsapp,
        String website,
        String instagram,
        String facebook,
        String address,
        @Display DisplayRef city,
        @Display DisplayRef state,
        String googleMapsUrl,
        String description,

        // B2B (ADR 0031 §9)
        String tradeName,
        String commercialZone,
        Short foundedYear,
        Integer cashRegistersCount,
        Integer branchesCount,
        Boolean hasPos,
        String representativePosition,

        // B2P practice site (ADR 0031 §4)
        String hostName,
        String officeNumber,
        String appointmentPhone,
        String assistantName,

        String logoUrl,
        boolean hasOpeningHours,
        boolean hasApprovedOffers,
        Completeness completeness
) {

    /**
     * Share of the recommended profile items already filled in, and the keys
     * of the missing ones ({@code logo}, {@code description}, {@code address},
     * {@code city}, {@code googleMapsUrl}, {@code contactPhone},
     * {@code openingHours}, {@code socialMedia}; B2P also
     * {@code practiceSite}, {@code appointmentPhone}, {@code licenses}). The portal translates
     * the keys.
     */
    public record Completeness(int percent, List<String> missing) {}
}
