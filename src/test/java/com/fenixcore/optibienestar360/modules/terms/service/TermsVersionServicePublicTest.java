package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.modules.catalog.repository.AllyTypeRepository;
import com.fenixcore.optibienestar360.modules.terms.dto.PublicTermsDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Anonymous public lookup of {@link TermsVersionService}: the ALIADO agreement
 * is split by segment (V217), so the public site must serve the segment
 * template — never the superseded general one — and list every current public
 * version.
 */
@ExtendWith(MockitoExtension.class)
class TermsVersionServicePublicTest {

    @Mock private TermsVersionRepository repository;
    @Mock private TermsTemplateRenderer renderer;
    @Mock private AllyTypeRepository allyTypeRepository;
    @Mock private AllyRepository allyRepository;
    @Mock private MessageSource messageSource;

    private TermsVersionService service;
    private final Instant now = Instant.now();

    @BeforeEach
    void setup() {
        service = new TermsVersionService(repository, renderer, allyTypeRepository, allyRepository, messageSource);
        lenient().when(renderer.render(anyString())).thenAnswer(inv -> "rendered:" + inv.getArgument(0));
    }

    @Test
    void aliadoWithSegment_servesThatSegmentsTemplate() {
        TermsVersion general = template(1L, TermType.ALLY, null, 30);
        TermsVersion b2b = template(2L, TermType.ALLY, Segment.B2B, 5);
        TermsVersion b2p = template(3L, TermType.ALLY, Segment.B2P, 5);
        when(repository.findByTermTypeOrderByValidFromDesc(TermType.ALLY)).thenReturn(List.of(b2b, b2p, general));

        PublicTermsDto dto = service.getCurrentPublicRendered(TermType.ALLY, Segment.B2P);

        assertThat(dto.uuid()).isEqualTo(b2p.getUuid());
        assertThat(dto.segment()).isEqualTo(Segment.B2P);
        assertThat(dto.contentMarkdown()).isEqualTo("rendered:texto 3");
    }

    @Test
    void aliadoWithoutSegment_isRejectedOnceSegmentTemplatesExist_insteadOfServingTheOldGeneralText() {
        TermsVersion general = template(1L, TermType.ALLY, null, 30);
        TermsVersion b2b = template(2L, TermType.ALLY, Segment.B2B, 5);
        when(repository.findByTermTypeOrderByValidFromDesc(TermType.ALLY)).thenReturn(List.of(b2b, general));

        assertThatThrownBy(() -> service.getCurrentPublic(TermType.ALLY, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms.public.segment_required");
    }

    @Test
    void aliadoWithoutSegment_servesTheGeneralTemplateWhileNoSegmentTemplateExists() {
        TermsVersion general = template(1L, TermType.ALLY, null, 30);
        when(repository.findByTermTypeOrderByValidFromDesc(TermType.ALLY)).thenReturn(List.of(general));

        assertThat(service.getCurrentPublic(TermType.ALLY, null)).isSameAs(general);
    }

    @Test
    void aliadoSegmentWithoutPublicTemplate_is404() {
        TermsVersion b2b = template(2L, TermType.ALLY, Segment.B2B, 5);
        when(repository.findByTermTypeOrderByValidFromDesc(TermType.ALLY)).thenReturn(List.of(b2b));

        assertThatThrownBy(() -> service.getCurrentPublic(TermType.ALLY, Segment.B2P))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("terms_version.not_found");
    }

    @Test
    void segmentOnAnotherType_isRejected() {
        assertThatThrownBy(() -> service.getCurrentPublic(TermType.MEMBER, Segment.B2B))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms.public.segment_only_aliado");
    }

    @Test
    void list_returnsOneRenderedVersionPerPublicStream() {
        TermsVersion member = template(1L, TermType.MEMBER, null, 5);
        TermsVersion advisorPrivate = template(2L, TermType.ADVISOR, null, 5);
        advisorPrivate.setPublic(false);
        TermsVersion oldGeneral = template(3L, TermType.ALLY, null, 30);
        TermsVersion b2b = template(4L, TermType.ALLY, Segment.B2B, 5);
        TermsVersion b2p = template(5L, TermType.ALLY, Segment.B2P, 5);
        when(repository.findAll()).thenReturn(List.of(member, advisorPrivate, oldGeneral, b2b, b2p));

        List<PublicTermsDto> list = service.listCurrentPublicRendered();

        assertThat(list).extracting(PublicTermsDto::uuid)
                .containsExactly(member.getUuid(), b2b.getUuid(), b2p.getUuid());
        assertThat(list.get(0).contentMarkdown()).isEqualTo("rendered:texto 1");
    }

    private TermsVersion template(long id, TermType type, Segment segment, int daysAgo) {
        TermsVersion version = new TermsVersion();
        version.setId(id);
        version.setUuid(UUID.randomUUID());
        version.setTermType(type);
        version.setTitle("Título " + id);
        version.setContentMarkdown("texto " + id);
        version.setValidFrom(now.minus(daysAgo, ChronoUnit.DAYS));
        version.setSegment(segment);
        version.setPublic(true);
        return version;
    }
}
