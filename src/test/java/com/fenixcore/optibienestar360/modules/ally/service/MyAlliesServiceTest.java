package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapperImpl;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link MyAlliesService}. Uses the real
 * {@code AllyMapperImpl} so the @Mapping source paths of
 * {@code toMyAllyDto} ({@code ally.uuid}, {@code ally.allyTypes} names) are
 * exercised end-to-end rather than stubbed away — the ally-vs-pivot UUID
 * choice is the contract the partner portal depends on.
 */
@ExtendWith(MockitoExtension.class)
class MyAlliesServiceTest {

    @Mock private AllyUserRepository repository;

    private MyAlliesService service;

    @BeforeEach
    void setup() {
        service = new MyAlliesService(repository, new AllyMapperImpl());
    }

    @Test
    void listMyAllies_withNoMembership_returnsEmptyList() {
        UUID userUuid = UUID.randomUUID();
        when(repository.findActiveByUserUuid(userUuid)).thenReturn(List.of());

        assertThat(service.listMyAllies(userUuid)).isEmpty();
    }

    @Test
    void listMyAllies_exposesAllyUuidNotPivotUuid() {
        UUID userUuid = UUID.randomUUID();
        Ally ally = ally("Clínica Vicente", "Clínica");
        AllyUser pivot = pivot(ally, AllyRole.STAFF, false);

        when(repository.findActiveByUserUuid(userUuid)).thenReturn(List.of(pivot));

        MyAllyDto dto = service.listMyAllies(userUuid).getFirst();

        // The portal feeds dto.uuid() straight into POST /v1/ally/benefit-usage
        // as allyUuid — handing back the pivot's uuid would 404 there.
        assertThat(dto.uuid()).isEqualTo(ally.getUuid());
        assertThat(dto.uuid()).isNotEqualTo(pivot.getUuid());
        assertThat(dto.name()).isEqualTo("Clínica Vicente");
        assertThat(dto.allyTypeNames()).containsExactly("Clínica");
        assertThat(dto.allyRole()).isEqualTo(AllyRole.STAFF);
        assertThat(dto.primary()).isFalse();
    }

    @Test
    void listMyAllies_ordersPrimaryFirstThenAlphabetically() {
        UUID userUuid = UUID.randomUUID();
        AllyUser zeta = pivot(ally("Zeta Salud", "Clínica"), AllyRole.STAFF, false);
        AllyUser alfa = pivot(ally("Alfa Salud", "Clínica"), AllyRole.STAFF, false);
        AllyUser primary = pivot(ally("Óptica Vicente", "Óptica"), AllyRole.OWNER, true);

        // Repository order is unspecified — the service is what sorts.
        when(repository.findActiveByUserUuid(userUuid)).thenReturn(List.of(zeta, alfa, primary));

        assertThat(service.listMyAllies(userUuid))
                .extracting(MyAllyDto::name)
                .containsExactly("Óptica Vicente", "Alfa Salud", "Zeta Salud");
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private Ally ally(String name, String typeName) {
        AllyType type = new AllyType();
        type.setId(1L);
        type.setUuid(UUID.randomUUID());
        type.setName(typeName);

        Ally ally = new Ally();
        ally.setId(1L);
        ally.setUuid(UUID.randomUUID());
        ally.setName(name);
        ally.setAllyTypes(Set.of(type));
        ally.setPhone("+58 212 5550100");
        return ally;
    }

    private AllyUser pivot(Ally ally, AllyRole role, boolean primary) {
        AllyUser au = new AllyUser();
        au.setId(2L);
        au.setUuid(UUID.randomUUID());
        au.setAlly(ally);
        au.setAllyRole(role);
        au.setPrimary(primary);
        au.setJoinedAt(LocalDate.of(2026, 1, 15));
        return au;
    }
}
