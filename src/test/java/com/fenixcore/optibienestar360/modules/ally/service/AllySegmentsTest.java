package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Segment rules of an ally (V209, hub ADR 0031): derivation from the types,
 * types matching the segment, the B2P holder person and the segment-specific
 * fields.
 */
class AllySegmentsTest {

    private final AllyType pharmacy = type(Segment.B2B);
    private final AllyType physician = type(Segment.B2P);

    @Test
    void derive_isB2pOnlyWhenEveryTypeIsB2p() {
        assertThat(AllySegments.derive(List.of(physician))).isEqualTo(Segment.B2P);
        assertThat(AllySegments.derive(List.of(pharmacy))).isEqualTo(Segment.B2B);
        assertThat(AllySegments.derive(List.of(pharmacy, physician))).isEqualTo(Segment.B2B);
        assertThat(AllySegments.derive(List.of())).isEqualTo(Segment.B2B);
    }

    @Test
    void requireTypesOf_rejectsATypeOfTheOtherSegment() {
        Ally mixed = ally(Segment.B2B, pharmacy, physician);

        assertThatThrownBy(() -> AllySegments.requireTypesOf(mixed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally.segment.type_mismatch");
        assertThatCode(() -> AllySegments.requireTypesOf(ally(Segment.B2P, physician))).doesNotThrowAnyException();
    }

    @Test
    void requireHolder_onlyForProfessionalAllies() {
        assertThatThrownBy(() -> AllySegments.requireHolder(ally(Segment.B2P, physician)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally.holder.required");
        assertThatCode(() -> AllySegments.requireHolder(ally(Segment.B2B, pharmacy))).doesNotThrowAnyException();

        Ally professional = ally(Segment.B2P, physician);
        professional.setHolderPerson(new Person());
        assertThatCode(() -> AllySegments.requireHolder(professional)).doesNotThrowAnyException();
    }

    @Test
    void rejectForeignFields_commercialFieldsOnAProfessionalAlly() {
        AllySegments.SiteFields commercial = fields(null, null, "Sucursal centro", null);

        assertThatThrownBy(() -> AllySegments.rejectForeignFields(Segment.B2P, commercial))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally.segment.field_not_allowed");
        assertThatCode(() -> AllySegments.rejectForeignFields(Segment.B2B, commercial)).doesNotThrowAnyException();
    }

    @Test
    void rejectForeignFields_practiceSiteFieldsOnACommercialAlly() {
        AllySegments.SiteFields site = fields(UUID.randomUUID(), "12-B", null, null);

        assertThatThrownBy(() -> AllySegments.rejectForeignFields(Segment.B2B, site))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally.segment.field_not_allowed");
        assertThatCode(() -> AllySegments.rejectForeignFields(Segment.B2P, site)).doesNotThrowAnyException();
    }

    @Test
    void rejectForeignFields_blankTextIsNotSending() {
        AllySegments.SiteFields blanks = fields(null, "  ", "", null);

        assertThatCode(() -> AllySegments.rejectForeignFields(Segment.B2B, blanks)).doesNotThrowAnyException();
        assertThatCode(() -> AllySegments.rejectForeignFields(Segment.B2P, blanks)).doesNotThrowAnyException();
    }

    @Test
    void clearForeignFields_wipesTheOtherSegmentsData() {
        Ally ally = ally(Segment.B2P, physician);
        ally.setTradeName("Farmacia Central");
        ally.setHasPos(true);
        ally.setOfficeNumber("12-B");

        AllySegments.clearForeignFields(ally);

        assertThat(ally.getTradeName()).isNull();
        assertThat(ally.getHasPos()).isNull();
        assertThat(ally.getOfficeNumber()).isEqualTo("12-B");
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private static AllySegments.SiteFields fields(UUID hostAllyUuid, String officeNumber, String tradeName, Boolean hasPos) {
        return new AllySegments.SiteFields(hostAllyUuid, null, officeNumber, null, null,
                null, tradeName, null, null, null, hasPos, null);
    }

    private static AllyType type(Segment segment) {
        AllyType type = new AllyType();
        type.setUuid(UUID.randomUUID());
        type.setCode("T-" + segment);
        type.setSegment(segment);
        return type;
    }

    private static Ally ally(Segment segment, AllyType... types) {
        Ally ally = new Ally();
        ally.setSegment(segment);
        ally.getAllyTypes().addAll(List.of(types));
        return ally;
    }
}
