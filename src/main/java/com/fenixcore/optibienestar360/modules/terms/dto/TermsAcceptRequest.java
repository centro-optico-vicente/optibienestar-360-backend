package com.fenixcore.optibienestar360.modules.terms.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Payload for {@code POST /v1/me/terms/accept} — one entry per checked box in
 * the modal.
 *
 * <ul>
 *   <li>{@code items}: the pending items accepted, each a
 *   {@code termsVersionUuid} plus, for ALIADO, the {@code allyAgreementUuid}
 *   from {@code GET /v1/me/terms/pending}.</li>
 *   <li>{@code termsVersionUuids}: <b>deprecated</b>, kept for clients older
 *   than V189 — a bare version UUID, valid for AFILIADO / ASESOR or an ALIADO
 *   version pending for a single agreement (otherwise 422
 *   {@code terms_acceptance.agreement_required}).</li>
 * </ul>
 * At least one of the two lists must be non-empty.
 */
public record TermsAcceptRequest(
        List<UUID> termsVersionUuids,
        @Valid List<Item> items
) {

    /** One accepted pending item; {@code allyAgreementUuid} only for ALIADO. */
    public record Item(@NotNull UUID termsVersionUuid, UUID allyAgreementUuid) {
    }

    @JsonIgnore
    @AssertTrue(message = "terms_acceptance.nothing_selected")
    public boolean isAnythingSelected() {
        return (termsVersionUuids != null && !termsVersionUuids.isEmpty())
                || (items != null && !items.isEmpty());
    }

    /** Both lists merged: {@code items} first, then each legacy UUID as an item without agreement. */
    @JsonIgnore
    public List<Item> allItems() {
        List<Item> all = new ArrayList<>();
        if (items != null) all.addAll(items);
        if (termsVersionUuids != null) {
            termsVersionUuids.forEach(uuid -> all.add(new Item(uuid, null)));
        }
        return all;
    }
}
