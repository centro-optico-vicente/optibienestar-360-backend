package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.AllyTypeRepository;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionCreateRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionUpdateRequest;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * {@code is_mandatory} (V192) in {@link TermsVersionService}: defaulted by
 * type on create, and — being policy, not contract text — the only field
 * that can still change once a version is vigente.
 */
@ExtendWith(MockitoExtension.class)
class TermsVersionMandatoryTest {

    @Mock private TermsVersionRepository repository;
    @Mock private TermsTemplateRenderer renderer;
    @Mock private AllyTypeRepository allyTypeRepository;
    @Mock private AllyRepository allyRepository;

    private TermsVersionService service;

    @BeforeEach
    void setup() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        service = new TermsVersionService(repository, renderer, allyTypeRepository, allyRepository, messageSource);
        lenient().when(repository.save(any(TermsVersion.class))).thenAnswer(inv -> {
            TermsVersion saved = inv.getArgument(0);
            saved.setUuid(UUID.randomUUID());
            return saved;
        });
        lenient().when(renderer.renderSample(anyString(), any())).thenReturn("vista previa");
    }

    @Test
    void create_withoutTheFlag_defaultsByType() {
        assertThat(service.create(createRequest(TermType.MEMBER, null)).isMandatory()).isTrue();
        assertThat(service.create(createRequest(TermType.ADVISOR, null)).isMandatory()).isFalse();
        assertThat(service.create(createRequest(TermType.ALLY, null)).isMandatory()).isFalse();
    }

    @Test
    void create_withTheFlag_keepsIt() {
        assertThat(service.create(createRequest(TermType.ADVISOR, true)).isMandatory()).isTrue();
        assertThat(service.create(createRequest(TermType.MEMBER, false)).isMandatory()).isFalse();
    }

    @Test
    void update_vigenteVersion_acceptsOnlyTheMandatoryFlag() {
        TermsVersion live = vigente(TermType.ADVISOR);

        assertThat(service.update(live.getUuid(), mandatoryOnly(true)).isMandatory()).isTrue();
        assertThat(live.isMandatory()).isTrue();
    }

    @Test
    void update_vigenteVersion_stillRejectsAnyOtherChange() {
        TermsVersion live = vigente(TermType.ADVISOR);
        UUID uuid = live.getUuid();
        TermsVersionUpdateRequest req = new TermsVersionUpdateRequest("Otro título", null, null, true,
                null, null, null, null);

        assertThatThrownBy(() -> service.update(uuid, req))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms_version.immutable");
        assertThat(live.isMandatory()).isFalse();
        assertThat(live.getTitle()).isEqualTo("Mandato");
    }

    private TermsVersion vigente(TermType type) {
        TermsVersion version = new TermsVersion();
        version.setId(9L);
        version.setUuid(UUID.randomUUID());
        version.setTermType(type);
        version.setTitle("Mandato");
        version.setContentMarkdown("Texto");
        version.setValidFrom(Instant.now().minus(3, ChronoUnit.DAYS));
        when(repository.findByUuid(version.getUuid())).thenReturn(Optional.of(version));
        return version;
    }

    private static TermsVersionUpdateRequest mandatoryOnly(boolean mandatory) {
        return new TermsVersionUpdateRequest(null, null, null, mandatory, null, null, null, null);
    }

    private static TermsVersionCreateRequest createRequest(TermType type, Boolean mandatory) {
        return new TermsVersionCreateRequest(type, "Título", "Texto", false, mandatory,
                Instant.now().minus(1, ChronoUnit.HOURS), null, null, null);
    }
}
