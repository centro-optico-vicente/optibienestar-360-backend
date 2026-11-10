package com.fenixcore.optibienestar360.modules.ally.dto;

import java.util.UUID;

/**
 * Master-table filters for ally lists (admin allies and the ally-requests inbox).
 * First-class query params rather than RSQL: ally types and professions are
 * many-to-many collections, which the scalar RSQL allow-list cannot express
 * without duplicating rows.
 *
 * @param allyTypeUuid  the ally has this type
 * @param professionUuid the ally has this profession
 * @param stateUuid     the ally's city belongs to this state
 * @param cityUuid      the ally's city
 * @param promoterUuid  only the allies this advisor brought in (404 when it does not exist)
 */
public record AllyListFilter(UUID allyTypeUuid, UUID professionUuid, UUID stateUuid, UUID cityUuid,
                             UUID promoterUuid) {

    public AllyListFilter(UUID allyTypeUuid, UUID professionUuid, UUID stateUuid, UUID cityUuid) {
        this(allyTypeUuid, professionUuid, stateUuid, cityUuid, null);
    }

    public static AllyListFilter none() {
        return new AllyListFilter(null, null, null, null, null);
    }
}
