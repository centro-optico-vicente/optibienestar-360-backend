package com.fenixcore.optibienestar360.modules.ally.mapper;

import com.fenixcore.optibienestar360.modules.ally.dto.AllyDetailDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Agreement file number (V179, hub ADR 0031 §10) — built by
 * {@link AllyMapper#fileNumber}: a professional (B2P) ally prints
 * "[CÓDIGO DE ASESOR]-[Nº DE ALIADO]", a commercial (B2B) ally just the
 * zero-padded number. Uses the real generated mapper.
 */
class AllyMapperFileNumberTest {

    private final AllyMapper mapper = new AllyMapperImpl();

    @Test
    void withCapturingPromoter_prefixesAdvisorCode() {
        Ally ally = ally(7, promoter("ABC123", "Ana Pérez"));

        assertThat(mapper.fileNumber(ally)).isEqualTo("ABC123-0007");
    }

    @Test
    void commercialAlly_isJustThePaddedNumber_evenWithCapturingPromoter() {
        Ally ally = ally(7, promoter("ABC123", "Ana Pérez"));
        ally.setSegment(AllyType.Segment.B2B);

        assertThat(mapper.fileNumber(ally)).isEqualTo("0007");
    }

    @Test
    void withoutPromoter_isJustThePaddedNumber() {
        assertThat(mapper.fileNumber(ally(42, null))).isEqualTo("0042");
    }

    @Test
    void promoterWithoutReferralCode_fallsBackToPaddedNumber() {
        assertThat(mapper.fileNumber(ally(3, promoter(null, "Sin código")))).isEqualTo("0003");
    }

    @Test
    void numberWiderThanPadding_isNotTruncated() {
        assertThat(mapper.fileNumber(ally(12345, promoter("XYZ9", "Luis")))).isEqualTo("XYZ9-12345");
    }

    @Test
    void notYetNumbered_isNull() {
        assertThat(mapper.fileNumber(ally(null, promoter("ABC123", "Ana")))).isNull();
    }

    @Test
    void toDetail_exposesNumberFileNumberAndPromoterRef() {
        Promoter promoter = promoter("ABC123", "Ana Pérez");
        Ally ally = ally(7, promoter);
        ally.setRepresentativeName("Dr. Juan Gómez");
        ally.setRepresentativeDocumentType("V");
        ally.setRepresentativeDocumentNumber("12345678");

        AllyDetailDto dto = mapper.toDetail(ally);

        assertThat(dto.allyNumber()).isEqualTo(7);
        assertThat(dto.fileNumber()).isEqualTo("ABC123-0007");
        assertThat(dto.capturedByPromoter().uuid()).isEqualTo(promoter.getUuid());
        assertThat(dto.capturedByPromoter().code()).isEqualTo("ABC123");
        assertThat(dto.capturedByPromoter().name()).isEqualTo("Ana Pérez");
        assertThat(dto.representativeName()).isEqualTo("Dr. Juan Gómez");
        assertThat(dto.representativeDocumentType()).isEqualTo("V");
        assertThat(dto.representativeDocumentNumber()).isEqualTo("12345678");
    }

    private static Ally ally(Integer number, Promoter promoter) {
        Ally ally = new Ally();
        ally.setId(1L);
        ally.setUuid(UUID.randomUUID());
        ally.setName("Óptica Vicente");
        ally.setAllyNumber(number);
        ally.setCapturedByPromoter(promoter);
        ally.setSegment(AllyType.Segment.B2P);
        return ally;
    }

    private static Promoter promoter(String referralCode, String displayName) {
        Promoter promoter = new Promoter();
        promoter.setId(9L);
        promoter.setUuid(UUID.randomUUID());
        promoter.setReferralCode(referralCode);
        promoter.setDisplayName(displayName);
        return promoter;
    }
}
