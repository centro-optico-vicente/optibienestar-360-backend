package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.PublicAllyServiceDto;
import com.fenixcore.optibienestar360.modules.ally.dto.PublicServiceListItemDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyService.ReviewStatus;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapperImpl;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyServiceRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.City;
import com.fenixcore.optibienestar360.modules.catalog.entity.ServiceCategory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicServicesService} — the anonymous read paths
 * behind {@code GET /v1/public/services} (cross-ally) and
 * {@code GET /v1/public/allies/{uuid}/services} (per-ally). Uses the real
 * {@code AllyMapperImpl} (same approach as {@code MyAlliesServiceTest}) so the
 * sanitized projections are exercised end-to-end.
 *
 * <p>The invariants locked here:</p>
 * <ul>
 *   <li>The per-ally path 404s when the ally is not publicly visible, and never
 *       touches the service repository in that case (no probing).</li>
 *   <li>The cross-ally row flattens the parent ally's identity so a search
 *       result stands on its own.</li>
 * </ul>
 * <p>The DB-level visibility predicate (service + ally both active/published,
 * {@code reviewStatus=APPROVED}) lives in a JPA {@link Specification}, so it is
 * asserted by the IT / CI against a real schema, not here.</p>
 */
@ExtendWith(MockitoExtension.class)
class PublicServicesServiceTest {

    @Mock private AllyServiceRepository serviceRepository;
    @Mock private AllyRepository allyRepository;

    private PublicServicesService service;

    @BeforeEach
    void setup() {
        service = new PublicServicesService(serviceRepository, allyRepository, new AllyMapperImpl());
    }

    // ─── listByAlly ──────────────────────────────────────────────────────────

    @Test
    void listByAlly_404s_whenAllyIsUnpublished_andNeverQueriesServices() {
        Ally ally = ally("Óptica Vicente", "Óptica", "Mérida");
        ally.setPublished(false);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));

        assertThatThrownBy(() -> service.listByAlly(ally.getUuid(), null, null, PageRequest.of(0, 20)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("ally.not_found");

        // Guard rejects before any service lookup — no probing an invisible ally.
        verify(serviceRepository, never()).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void listByAlly_404s_whenAllyDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(allyRepository.findByUuid(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listByAlly(missing, null, null, PageRequest.of(0, 20)))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("ally.not_found");
    }

    @Test
    void listByAlly_returnsLeanServiceDtos_forVisibleAlly() {
        Ally ally = ally("Óptica Vicente", "Óptica", "Mérida");
        AllyService svc = service("Examen de la vista", "Optometría", ally);
        when(allyRepository.findByUuid(ally.getUuid())).thenReturn(Optional.of(ally));
        when(serviceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(svc)));

        Page<PublicAllyServiceDto> page = service.listByAlly(ally.getUuid(), null, null, PageRequest.of(0, 20));

        PublicAllyServiceDto dto = page.getContent().getFirst();
        assertThat(dto.uuid()).isEqualTo(svc.getUuid());
        assertThat(dto.name()).isEqualTo("Examen de la vista");
        assertThat(dto.categoryName()).isEqualTo("Optometría");
        assertThat(dto.priceAmount()).isEqualByComparingTo("15.00");
    }

    // ─── search (cross-ally) ─────────────────────────────────────────────────

    @Test
    void search_flattensParentAllyIdentityOntoEachRow() {
        Ally ally = ally("Óptica Vicente", "Óptica", "Mérida");
        AllyService svc = service("Examen de la vista", "Optometría", ally);
        Pageable pageable = PageRequest.of(0, 20);
        when(serviceRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(svc), pageable, 1));

        Page<PublicServiceListItemDto> page = service.search(null, null, null, pageable);

        assertThat(page.getTotalElements()).isEqualTo(1);
        PublicServiceListItemDto row = page.getContent().getFirst();
        // Service fields.
        assertThat(row.uuid()).isEqualTo(svc.getUuid());
        assertThat(row.name()).isEqualTo("Examen de la vista");
        assertThat(row.categoryName()).isEqualTo("Optometría");
        // Parent-ally identity flattened onto the row.
        assertThat(row.allyUuid()).isEqualTo(ally.getUuid());
        assertThat(row.allyName()).isEqualTo("Óptica Vicente");
        assertThat(row.allyTypeNames()).containsExactly("Óptica");
        assertThat(row.allyCityName()).isEqualTo("Mérida");
        assertThat(row.allyPhone()).isEqualTo("+58 274 5550100");
    }

    // ─── Fixtures ────────────────────────────────────────────────────────────

    private Ally ally(String name, String typeName, String cityName) {
        AllyType type = new AllyType();
        type.setId(1L);
        type.setUuid(UUID.randomUUID());
        type.setName(typeName);

        City city = new City();
        city.setId(1L);
        city.setUuid(UUID.randomUUID());
        city.setName(cityName);

        Ally ally = new Ally();
        ally.setId(1L);
        ally.setUuid(UUID.randomUUID());
        ally.setName(name);
        ally.setAllyTypes(Set.of(type));
        ally.setCity(city);
        ally.setPhone("+58 274 5550100");
        ally.setLogoUrl("https://cdn.example/logo.png");
        ally.setActive(true);
        ally.setPublished(true);
        return ally;
    }

    private AllyService service(String name, String categoryName, Ally ally) {
        ServiceCategory category = new ServiceCategory();
        category.setId(1L);
        category.setUuid(UUID.randomUUID());
        category.setName(categoryName);

        AllyService svc = new AllyService();
        svc.setId(2L);
        svc.setUuid(UUID.randomUUID());
        svc.setAlly(ally);
        svc.setServiceCategory(category);
        svc.setName(name);
        svc.setDescription("Servicio de prueba");
        svc.setPriceAmount(new BigDecimal("15.00"));
        svc.setRequiresAppointment(true);
        svc.setReviewStatus(ReviewStatus.APPROVED);
        svc.setActive(true);
        svc.setPublished(true);
        return svc;
    }
}
