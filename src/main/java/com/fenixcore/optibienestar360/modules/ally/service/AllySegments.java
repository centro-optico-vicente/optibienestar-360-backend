package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;

import java.util.Collection;
import java.util.UUID;

/**
 * Segment rules of an ally (V209, hub ADR 0031), stateless so plain unit
 * tests can call them:
 *
 * <ul>
 *   <li>The segment is the ally's own; every type it carries belongs to it.</li>
 *   <li>A B2P ally ("Aliado Profesional") needs its holder person — the professional.</li>
 *   <li>The practice-site fields are B2P-only and the commercial fields
 *       B2B-only: sending the other segment's fields is rejected, and they
 *       are wiped when the segment changes.</li>
 * </ul>
 */
public final class AllySegments {

    private AllySegments() {
    }

    /** B2P when every type is B2P, else B2B (also the V209 backfill rule). */
    public static Segment derive(Collection<AllyType> types) {
        boolean allProfessional = types != null && !types.isEmpty()
                && types.stream().allMatch(t -> t.getSegment() == Segment.B2P);
        return allProfessional ? Segment.B2P : Segment.B2B;
    }

    /** Every type of the ally belongs to its segment — 422 {@code ally.segment.type_mismatch} otherwise. */
    public static void requireTypesOf(Ally ally) {
        for (AllyType type : ally.getAllyTypes()) {
            requireTypeOf(ally.getSegment(), type);
        }
    }

    public static void requireTypeOf(Segment segment, AllyType type) {
        if (type.getSegment() != segment) {
            throw new IllegalArgumentException("ally.segment.type_mismatch");
        }
    }

    /** A B2P ally needs its professional — 422 {@code ally.holder.required}. */
    public static void requireHolder(Ally ally) {
        if (ally.getSegment() == Segment.B2P && ally.getHolderPerson() == null) {
            throw new IllegalArgumentException("ally.holder.required");
        }
    }

    /** Rejects (422 {@code ally.segment.field_not_allowed}) the fields of the other segment. */
    public static void rejectForeignFields(Segment segment, SiteFields f) {
        boolean foreign = segment == Segment.B2P ? f.hasCommercialFields() : f.hasPracticeSiteFields();
        if (foreign) {
            throw new IllegalArgumentException("ally.segment.field_not_allowed");
        }
    }

    /** Wipes the fields that do not belong to the ally's (new) segment. */
    public static void clearForeignFields(Ally ally) {
        if (ally.getSegment() == Segment.B2P) {
            ally.setCommercialZone(null);
            ally.setTradeName(null);
            ally.setFoundedYear(null);
            ally.setCashRegistersCount(null);
            ally.setBranchesCount(null);
            ally.setHasPos(null);
            ally.setRepresentativePosition(null);
        } else {
            ally.setHostAlly(null);
            ally.setHostName(null);
            ally.setOfficeNumber(null);
            ally.setAppointmentPhone(null);
            ally.setAssistantName(null);
        }
    }

    /** The segment-specific fields of a create / update payload. */
    public record SiteFields(
            UUID hostAllyUuid, String hostName, String officeNumber, String appointmentPhone, String assistantName,
            String commercialZone, String tradeName, Short foundedYear, Integer cashRegistersCount,
            Integer branchesCount, Boolean hasPos, String representativePosition) {

        public static SiteFields of(AllyCreateRequest r) {
            return new SiteFields(r.hostAllyUuid(), r.hostName(), r.officeNumber(), r.appointmentPhone(),
                    r.assistantName(), r.commercialZone(), r.tradeName(), r.foundedYear(),
                    r.cashRegistersCount(), r.branchesCount(), r.hasPos(), r.representativePosition());
        }

        public static SiteFields of(AllyUpdateRequest r) {
            return new SiteFields(r.hostAllyUuid(), r.hostName(), r.officeNumber(), r.appointmentPhone(),
                    r.assistantName(), r.commercialZone(), r.tradeName(), r.foundedYear(),
                    r.cashRegistersCount(), r.branchesCount(), r.hasPos(), r.representativePosition());
        }

        /** Blank text counts as absent, so clearing a field is never "sending" it. */
        boolean hasPracticeSiteFields() {
            return hostAllyUuid != null || present(hostName) || present(officeNumber)
                    || present(appointmentPhone) || present(assistantName);
        }

        boolean hasCommercialFields() {
            return present(commercialZone) || present(tradeName) || foundedYear != null
                    || cashRegistersCount != null || branchesCount != null || hasPos != null
                    || present(representativePosition);
        }

        private static boolean present(String value) {
            return value != null && !value.isBlank();
        }
    }
}
