package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementCreateRequest;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementDto;
import com.fenixcore.optibienestar360.modules.ally.dto.AllyAgreementUpdateRequest;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement.AgreementType;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapperImpl;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyAgreementRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * V179: an ally agreement may only reference the standard {@code ALIADO}
 * terms version — affiliate / promoter T&amp;C are rejected with 422
 * {@code ally_agreement.terms_version.invalid_type}. V183: the template's
 * scope must match the ally (422
 * {@code ally_agreement.terms_version.not_applicable}). Also covers the
 * auto-renew / notice-days defaults and overrides.
 */
@ExtendWith(MockitoExtension.class)
class AllyAgreementsServiceTermsVersionTest {

    @Mock private AllyRepository allyRepository;
    @Mock private AllyAgreementRepository agreementRepository;
    @Mock private TermsVersionRepository termsVersionRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    private AllyAgreementsService service;
    private Ally ally;

    @BeforeEach
    void setup() {
        service = new AllyAgreementsService(allyRepository, agreementRepository, termsVersionRepository,
                new AllyMapperImpl(), defaultSortResolver);
        ally = new Ally();
        ally.setId(1L);
        ally.setUuid(UUID.randomUUID());
    }

    @Test
    void create_withAllyTermsVersion_linksItAndAppliesDefaults() {
        TermsVersion version = version(TermType.ALIADO);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(termsVersionRepository.findByUuid(version.getUuid())).thenReturn(Optional.of(version));
        when(agreementRepository.save(any(AllyAgreement.class))).thenAnswer(inv -> inv.getArgument(0));

        AllyAgreementDto dto = service.create(ally.getUuid(), createRequest(version.getUuid(), null, null));

        assertThat(dto.termsVersion().uuid()).isEqualTo(version.getUuid());
        assertThat(dto.termsVersion().name()).isEqualTo("Convenio de Alianza Estratégica (01-01-2026)");
        assertThat(dto.autoRenew()).isTrue();
        assertThat(dto.noticeDays()).isEqualTo(30);
    }

    @Test
    void create_overridesAutoRenewAndNoticeDays() {
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(agreementRepository.save(any(AllyAgreement.class))).thenAnswer(inv -> inv.getArgument(0));

        AllyAgreementDto dto = service.create(ally.getUuid(), createRequest(null, false, 60));

        assertThat(dto.termsVersion()).isNull();
        assertThat(dto.autoRenew()).isFalse();
        assertThat(dto.noticeDays()).isEqualTo(60);
    }

    @Test
    void create_withNonAllyTermsVersion_isRejected() {
        TermsVersion version = version(TermType.AFILIADO);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(termsVersionRepository.findByUuid(version.getUuid())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.create(ally.getUuid(), createRequest(version.getUuid(), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_agreement.terms_version.invalid_type");
        verify(agreementRepository, never()).save(any());
    }

    @Test
    void create_withUnknownTermsVersion_isNotFound() {
        UUID missing = UUID.randomUUID();
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(termsVersionRepository.findByUuid(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ally.getUuid(), createRequest(missing, null, null)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("terms_version.not_found");
    }

    @Test
    void update_withPromoterTermsVersion_isRejected() {
        AllyAgreement agreement = new AllyAgreement();
        agreement.setId(5L);
        agreement.setUuid(UUID.randomUUID());
        agreement.setAlly(ally);
        TermsVersion version = version(TermType.PROMOTOR);
        when(agreementRepository.findByUuid(agreement.getUuid())).thenReturn(Optional.of(agreement));
        when(termsVersionRepository.findByUuid(version.getUuid())).thenReturn(Optional.of(version));

        AllyAgreementUpdateRequest req = new AllyAgreementUpdateRequest(
                null, null, null, null, null, null, null, version.getUuid(), null, null);

        assertThatThrownBy(() -> service.update(ally.getUuid(), agreement.getUuid(), req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_agreement.terms_version.invalid_type");
        assertThat(agreement.getTermsVersion()).isNull();
    }

    @Test
    void create_withTemplateScopedToAnotherSegment_isNotApplicable() {
        ally.getAllyTypes().add(allyType(AllyType.Segment.B2B));
        TermsVersion version = version(TermType.ALIADO);
        version.setSegment(AllyType.Segment.B2P);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(termsVersionRepository.findByUuid(version.getUuid())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.create(ally.getUuid(), createRequest(version.getUuid(), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_agreement.terms_version.not_applicable");
        verify(agreementRepository, never()).save(any());
    }

    @Test
    void create_withTemplateMadeForAnotherAlly_isNotApplicable() {
        Ally other = new Ally();
        other.setId(2L);
        other.setUuid(UUID.randomUUID());
        TermsVersion version = version(TermType.ALIADO);
        version.setAlly(other);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(termsVersionRepository.findByUuid(version.getUuid())).thenReturn(Optional.of(version));

        assertThatThrownBy(() -> service.create(ally.getUuid(), createRequest(version.getUuid(), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("ally_agreement.terms_version.not_applicable");
    }

    @Test
    void create_withTemplateScopedToTheAllysSegment_linksIt() {
        ally.getAllyTypes().add(allyType(AllyType.Segment.B2P));
        TermsVersion version = version(TermType.ALIADO);
        version.setSegment(AllyType.Segment.B2P);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(termsVersionRepository.findByUuid(version.getUuid())).thenReturn(Optional.of(version));
        when(agreementRepository.save(any(AllyAgreement.class))).thenAnswer(inv -> inv.getArgument(0));

        AllyAgreementDto dto = service.create(ally.getUuid(), createRequest(version.getUuid(), null, null));

        assertThat(dto.termsVersion().uuid()).isEqualTo(version.getUuid());
    }

    @Test
    void update_resendingTheLinkedVersion_skipsTheApplicabilityCheck() {
        TermsVersion version = version(TermType.ALIADO);
        version.setSegment(AllyType.Segment.B2P); // no longer matches: the ally has no types now
        AllyAgreement agreement = new AllyAgreement();
        agreement.setId(5L);
        agreement.setUuid(UUID.randomUUID());
        agreement.setAlly(ally);
        agreement.setTermsVersion(version);
        when(agreementRepository.findByUuid(agreement.getUuid())).thenReturn(Optional.of(agreement));

        AllyAgreementUpdateRequest req = new AllyAgreementUpdateRequest(
                null, null, null, "Nuevo anexo", null, null, null, version.getUuid(), null, null);

        AllyAgreementDto dto = service.update(ally.getUuid(), agreement.getUuid(), req);

        assertThat(dto.terms()).isEqualTo("Nuevo anexo");
        assertThat(dto.termsVersion().uuid()).isEqualTo(version.getUuid());
        verify(termsVersionRepository, never()).findByUuid(any());
    }

    private static AllyType allyType(AllyType.Segment segment) {
        AllyType type = new AllyType();
        type.setId(9L);
        type.setUuid(UUID.randomUUID());
        type.setCode("TIPO_" + segment.name());
        type.setName("Tipo " + segment.name());
        type.setSegment(segment);
        return type;
    }

    private static AllyAgreementCreateRequest createRequest(UUID termsVersionUuid, Boolean autoRenew, Integer noticeDays) {
        return new AllyAgreementCreateRequest(AgreementType.COMMERCIAL, LocalDate.of(2026, 1, 1), null,
                null, null, null, termsVersionUuid, autoRenew, noticeDays);
    }

    private static TermsVersion version(TermType type) {
        TermsVersion version = new TermsVersion();
        version.setId(3L);
        version.setUuid(UUID.randomUUID());
        version.setTermType(type);
        version.setTitle("Convenio de Alianza Estratégica");
        // 2026-01-01T12:00 in Caracas (UTC-4).
        version.setValidFrom(Instant.parse("2026-01-01T16:00:00Z"));
        return version;
    }
}
