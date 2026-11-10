package com.fenixcore.optibienestar360.modules.allyrequest.dto;

import com.fenixcore.optibienestar360.core.display.Display;
import com.fenixcore.optibienestar360.core.display.DisplayRef;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Row of the ally request inbox ({@code GET /v1/admin/ally-requests}). */
public record AllyRequestListItemDto(
        UUID uuid,
        Integer allyNumber,
        String name,
        @Display(value = Display.Kind.ENUM, enumScope = "ally_type.segment") Segment segment,
        List<String> allyTypeNames,
        @Display(value = Display.Kind.ENUM, enumScope = "ally.registration_status") Ally.RegistrationStatus registrationStatus,
        @Display(value = Display.Kind.ENUM, enumScope = "ally.registration_channel") Ally.RegistrationChannel registrationChannel,
        String contactName,
        String contactEmail,
        String contactPhone,
        @Display DisplayRef city,
        /** Advisor who brought the ally in; {@code promoter_Uuid} / {@code promoter_Display}, null = none. */
        @Display DisplayRef promoter,
        String registrationNote,
        @Display(Display.Kind.DATETIME) Instant createdAt,
        boolean hasUser,
        boolean hasActiveAgreement,
        /** Draft held out of the abandoned-draft cleanup (V237); only meaningful while the registration is in progress. */
        boolean retainFromCleanup,
        String retainNote,
        @Display(Display.Kind.DATETIME) Instant retainedAt
) {}
