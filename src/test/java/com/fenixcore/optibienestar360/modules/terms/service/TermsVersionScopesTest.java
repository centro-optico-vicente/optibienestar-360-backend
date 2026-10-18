package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * V183 applicability of ALIADO templates: a scope field, when set, must match
 * the ally (the ally itself, one of its types, the segment of one of its
 * types); the current version is resolved per scope stream; the agreement
 * form gets the most specific template first.
 */
class TermsVersionScopesTest {

    private final Instant now = Instant.now();
    private AllyType pharmacy;
    private AllyType physician;
    private Ally ally;
    private Ally otherAlly;

    @BeforeEach
    void setup() {
        pharmacy = allyType(1L, "FARMACIA", Segment.B2B);
        physician = allyType(2L, "PROFESIONAL_MEDICO", Segment.B2P);
        ally = ally(100L, pharmacy);
        otherAlly = ally(200L, physician);
    }

    @Test
    void generalTemplate_appliesToEveryAlly() {
        TermsVersion general = template(1L, null, null, null, 10);

        assertThat(TermsVersionScopes.isApplicableTo(general, ally)).isTrue();
        assertThat(TermsVersionScopes.isApplicableTo(general, otherAlly)).isTrue();
    }

    @Test
    void segmentScope_matchesTheSegmentOfAnyOfTheAllysTypes() {
        TermsVersion b2b = template(1L, null, Segment.B2B, null, 10);

        assertThat(TermsVersionScopes.isApplicableTo(b2b, ally)).isTrue();
        assertThat(TermsVersionScopes.isApplicableTo(b2b, otherAlly)).isFalse();
    }

    @Test
    void allyTypeScope_matchesAnyOfTheAllysTypes() {
        TermsVersion forPhysicians = template(1L, physician, null, null, 10);
        Ally mixed = ally(300L, pharmacy, physician);

        assertThat(TermsVersionScopes.isApplicableTo(forPhysicians, ally)).isFalse();
        assertThat(TermsVersionScopes.isApplicableTo(forPhysicians, mixed)).isTrue();
    }

    @Test
    void allyScope_matchesOnlyThatAlly() {
        TermsVersion custom = template(1L, null, null, ally, 10);

        assertThat(TermsVersionScopes.isApplicableTo(custom, ally)).isTrue();
        assertThat(TermsVersionScopes.isApplicableTo(custom, otherAlly)).isFalse();
    }

    @Test
    void nonAllyTerms_neverApply() {
        TermsVersion afiliado = template(1L, null, null, null, 10);
        afiliado.setTermType(TermType.AFILIADO);

        assertThat(TermsVersionScopes.isApplicableTo(afiliado, ally)).isFalse();
    }

    @Test
    void currentApplicable_isOnePerStream_mostSpecificFirst_andSkipsScheduledOrForeign() {
        TermsVersion generalOld = template(1L, null, null, null, 30);
        TermsVersion generalNew = template(2L, null, null, null, 5);
        TermsVersion generalScheduled = template(3L, null, null, null, -2);
        TermsVersion segment = template(4L, null, Segment.B2B, null, 20);
        TermsVersion type = template(5L, pharmacy, null, null, 20);
        TermsVersion custom = template(6L, null, null, ally, 20);
        TermsVersion foreignType = template(7L, physician, null, null, 20);
        TermsVersion foreignAlly = template(8L, null, null, otherAlly, 20);

        List<TermsVersion> result = TermsVersionScopes.currentApplicableTo(
                List.of(generalOld, generalNew, generalScheduled, segment, type, custom, foreignType, foreignAlly),
                ally, now);

        assertThat(result).containsExactly(custom, type, segment, generalNew);
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private TermsVersion template(long id, AllyType allyType, Segment segment, Ally scopedAlly, int daysAgo) {
        TermsVersion version = new TermsVersion();
        version.setId(id);
        version.setUuid(UUID.randomUUID());
        version.setTermType(TermType.ALIADO);
        version.setTitle("Convenio " + id);
        version.setValidFrom(now.minus(daysAgo, ChronoUnit.DAYS));
        version.setAllyType(allyType);
        version.setSegment(segment);
        version.setAlly(scopedAlly);
        return version;
    }

    private static AllyType allyType(long id, String code, Segment segment) {
        AllyType type = new AllyType();
        type.setId(id);
        type.setUuid(UUID.randomUUID());
        type.setCode(code);
        type.setName(code);
        type.setSegment(segment);
        return type;
    }

    private static Ally ally(long id, AllyType... types) {
        Ally ally = new Ally();
        ally.setId(id);
        ally.setUuid(UUID.randomUUID());
        ally.getAllyTypes().addAll(List.of(types));
        return ally;
    }
}
