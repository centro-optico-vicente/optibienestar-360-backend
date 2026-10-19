package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.core.exception.UnprocessableDetailsException;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.AllyTypeRepository;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVariableDto;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionCreateRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsVersionUpdateRequest;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Template variables in {@link TermsVersionService}: the per-type list for
 * the editor, the 422 on unknown variables at create/update, and the
 * sample-data preview.
 */
@ExtendWith(MockitoExtension.class)
class TermsVersionServiceVariablesTest {

    @Mock private TermsVersionRepository repository;
    @Mock private TermsTemplateRenderer renderer;
    @Mock private AllyTypeRepository allyTypeRepository;
    @Mock private AllyRepository allyRepository;

    private TermsVersionService service;

    @BeforeEach
    void setup() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setFallbackToSystemLocale(false);
        service = new TermsVersionService(repository, renderer, allyTypeRepository, allyRepository, messageSource);
        lenient().when(repository.save(any(TermsVersion.class))).thenAnswer(inv -> {
            TermsVersion saved = inv.getArgument(0);
            saved.setUuid(UUID.randomUUID());
            return saved;
        });
        lenient().when(renderer.renderSample(anyString(), any())).thenReturn("vista previa");
    }

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void listVariables_returnsTheTypesVariablesWithLocalizedDescriptions() {
        LocaleContextHolder.setLocale(Locale.forLanguageTag("es"));

        List<TermsVariableDto> variables = service.listVariables(TermType.ADVISOR);

        assertThat(variables).extracting(TermsVariableDto::key)
                .contains("brand.name", "org.taxId", "advisor.code", "advisor.rank")
                .doesNotContain("member.fullName", "ally.name");
        assertThat(variables).filteredOn(v -> v.key().equals("advisor.fullName"))
                .extracting(TermsVariableDto::description).containsExactly("Nombre completo del asesor");
        assertThat(variables).allSatisfy(v -> assertThat(v.description()).isNotEqualTo(v.key()));

        LocaleContextHolder.setLocale(Locale.ENGLISH);
        assertThat(service.listVariables(TermType.ADVISOR)).filteredOn(v -> v.key().equals("advisor.fullName"))
                .extracting(TermsVariableDto::description).containsExactly("Advisor full name");
    }

    @Test
    void create_withVariablesOfAnotherTypeOrUnknownOnes_is422ListingThem() {
        TermsVersionCreateRequest req = createRequest(TermType.MEMBER,
                "{{member.fullName}} {{advisor.code}} {{foo.bar}} {{advisor.code}}");

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(UnprocessableDetailsException.class)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms_version.unknown_variables")
                .satisfies(ex -> {
                    UnprocessableDetailsException e = (UnprocessableDetailsException) ex;
                    assertThat(e.getArgs()).containsExactly("advisor.code, foo.bar");
                    assertThat(e.getProperties()).containsEntry("invalidVariables", List.of("advisor.code", "foo.bar"));
                });
        verify(repository, never()).save(any());
    }

    @Test
    void create_withOnlyValidVariables_savesAndPreviewsWithSamples() {
        TermsVersionCreateRequest req = createRequest(TermType.ALLY, "{{org.city}} {{ally.fileNumber}}");

        assertThat(service.create(req).contentRendered()).isEqualTo("vista previa");
        verify(renderer).renderSample("{{org.city}} {{ally.fileNumber}}", TermType.ALLY);
    }

    @Test
    void update_validatesTheNewContentAgainstTheVersionsType() {
        TermsVersion scheduled = new TermsVersion();
        scheduled.setId(9L);
        scheduled.setUuid(UUID.randomUUID());
        scheduled.setTermType(TermType.ADVISOR);
        scheduled.setTitle("Mandato");
        scheduled.setContentMarkdown("{{advisor.code}}");
        scheduled.setValidFrom(Instant.now().plus(3, ChronoUnit.DAYS));
        when(repository.findByUuid(scheduled.getUuid())).thenReturn(Optional.of(scheduled));
        TermsVersionUpdateRequest req = new TermsVersionUpdateRequest(null, "{{ally.name}}", null, null, null, null, null);

        assertThatThrownBy(() -> service.update(scheduled.getUuid(), req))
                .isInstanceOf(UnprocessableDetailsException.class);
        assertThat(scheduled.getContentMarkdown()).isEqualTo("{{advisor.code}}");
    }

    private static TermsVersionCreateRequest createRequest(TermType type, String content) {
        return new TermsVersionCreateRequest(type, "Título", content, false,
                Instant.now().minus(1, ChronoUnit.HOURS), null, null, null);
    }
}
