package com.fenixcore.optibienestar360.modules.ally.service;

import com.fenixcore.optibienestar360.modules.ally.dto.UserAllyDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser.AllyRole;
import com.fenixcore.optibienestar360.modules.ally.mapper.AllyMapperImpl;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AllyUsersService#listAlliesForUser} — the reverse
 * lookup backing {@code GET /v1/admin/users/{userUuid}/allies}. Uses the
 * real {@code AllyMapperImpl} (same rationale as {@code MyAlliesServiceTest})
 * so the {@code ally.uuid}/{@code ally.name} mapping paths of
 * {@code toUserAllyDto} are exercised end-to-end.
 */
@ExtendWith(MockitoExtension.class)
class AllyUsersServiceListAlliesForUserTest {

    @Mock private AllyRepository allyRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private UserRepository userRepository;
    @Mock private DefaultSortResolver defaultSortResolver;

    private AllyUsersService service;

    @BeforeEach
    void setup() {
        service = new AllyUsersService(allyRepository, allyUserRepository, userRepository, new AllyMapperImpl(), defaultSortResolver);
    }

    @Test
    void unknownUser_throwsNotFound() {
        UUID userUuid = UUID.randomUUID();
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listAlliesForUser(userUuid))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("user.not_found");

        verifyNoInteractions(allyUserRepository);
    }

    @Test
    void userWithNoMemberships_returnsEmptyList() {
        UUID userUuid = UUID.randomUUID();
        User user = user(1L, userUuid);
        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(allyUserRepository.findByUserIdAndActiveTrue(1L)).thenReturn(List.of());

        assertThat(service.listAlliesForUser(userUuid)).isEmpty();
    }

    @Test
    void userWithMemberships_returnsFlattenedAllyInfo() {
        UUID userUuid = UUID.randomUUID();
        User user = user(1L, userUuid);
        Ally ally = ally("Optica Central");
        AllyUser pivot = pivot(ally, AllyRole.OWNER, true);

        when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        when(allyUserRepository.findByUserIdAndActiveTrue(1L)).thenReturn(List.of(pivot));

        UserAllyDto dto = service.listAlliesForUser(userUuid).getFirst();

        assertThat(dto.ally().uuid()).isEqualTo(ally.getUuid());
        assertThat(dto.ally().name()).isEqualTo("Optica Central");
        assertThat(dto.allyRole()).isEqualTo(AllyRole.OWNER);
        assertThat(dto.primary()).isTrue();
        assertThat(dto.joinedAt()).isEqualTo(LocalDate.of(2026, 1, 15));
        assertThat(dto.active()).isTrue();
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private User user(long id, UUID uuid) {
        User u = new User();
        u.setId(id);
        u.setUuid(uuid);
        return u;
    }

    private Ally ally(String name) {
        Ally ally = new Ally();
        ally.setId(1L);
        ally.setUuid(UUID.randomUUID());
        ally.setName(name);
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
        au.setActive(true);
        return au;
    }
}
