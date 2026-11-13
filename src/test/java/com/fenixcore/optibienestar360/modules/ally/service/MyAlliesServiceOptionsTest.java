package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.core.dto.OptionDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapper;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyProductRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * {@code GET /v1/me/allies/options} filters the caller's own allies in memory: case- and accent-insensitive
 * ({@code ñ} ≈ {@code n}, {@code ó} ≈ {@code o}), primary ally first, capped by {@code limit}.
 */
@ExtendWith(MockitoExtension.class)
class MyAlliesServiceOptionsTest {

    @Mock
    private AllyUserRepository allyUserRepository;
    @Mock
    private AllyMapper mapper;
    @Mock
    private AllyProductRepository allyProductRepository;

    @InjectMocks
    private MyAlliesService service;

    private final UUID userUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(allyUserRepository.findActiveByUserUuid(userUuid))
                .thenReturn(List.of(link("Clínica Norte", false), link("Óptica Peña", true)));
    }

    @Test
    void noQuery_returnsAll_primaryFirst() {
        List<OptionDto> options = service.listMyAllyOptions(userUuid, null, 50);

        assertThat(options).extracting(OptionDto::label).containsExactly("Óptica Peña", "Clínica Norte");
        assertThat(options).allMatch(o -> o.code() == null && o.active());
    }

    @Test
    void query_ignoresAccentsEnyeAndCase() {
        assertThat(service.listMyAllyOptions(userUuid, "pena", 50)).extracting(OptionDto::label).containsExactly("Óptica Peña");
        assertThat(service.listMyAllyOptions(userUuid, "PEÑA", 50)).extracting(OptionDto::label).containsExactly("Óptica Peña");
        assertThat(service.listMyAllyOptions(userUuid, "optica", 50)).extracting(OptionDto::label).containsExactly("Óptica Peña");
        assertThat(service.listMyAllyOptions(userUuid, "clinica", 50)).extracting(OptionDto::label).containsExactly("Clínica Norte");
    }

    @Test
    void limit_capsTheResult() {
        assertThat(service.listMyAllyOptions(userUuid, null, 1)).hasSize(1);
    }

    private static AllyUser link(String allyName, boolean primary) {
        Ally ally = new Ally();
        ally.setUuid(UUID.randomUUID());
        ally.setName(allyName);
        AllyUser link = new AllyUser();
        link.setAlly(ally);
        link.setPrimary(primary);
        return link;
    }
}
