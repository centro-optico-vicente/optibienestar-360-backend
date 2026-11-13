package com.fenixcore.optibienestar360.modules.onboarding.service;

import com.fenixcore.optibienestar360.modules.affiliation.entity.AffiliationRequest;
import com.fenixcore.optibienestar360.modules.affiliation.repository.AffiliationRequestRepository;
import com.fenixcore.optibienestar360.modules.ally.dto.MyAllyProfileDto;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.ally.service.MyAllyProfileService;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType.Segment;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Which registration {@link RegistrationProgressService#get(UUID, UUID)} shows
 * for an account with one in each segment (backend#438): the one of the active
 * role. Showing it decides nothing else — each registration is still reviewed
 * and approved on its own.
 */
@ExtendWith(MockitoExtension.class)
class RegistrationProgressByRoleTest {

    @Mock private AffiliationRequestRepository affiliationRequests;
    @Mock private AllyUserRepository allyUsers;
    @Mock private MyAllyProfileService allyProfiles;
    @Mock private RoleRepository roleRepository;

    @InjectMocks
    private RegistrationProgressService service;

    private final UUID userUuid = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(allyProfiles.completenessOf(any())).thenReturn(new MyAllyProfileDto.Completeness(0, List.of("x")));
    }

    private UUID role(String name) {
        Role role = new Role();
        role.setUuid(UUID.randomUUID());
        role.setName(name);
        lenient().when(roleRepository.findByUuid(role.getUuid())).thenReturn(Optional.of(role));
        return role.getUuid();
    }

    /** An affiliation the account already got approved (a member who now registers an ally). */
    private void approvedAffiliation() {
        AffiliationRequest approved = new AffiliationRequest();
        approved.setStatus(AffiliationRequest.Status.APPROVED.name());
        lenient().when(affiliationRequests.findFirstByUser_UuidOrderByCreatedAtDesc(userUuid))
                .thenReturn(Optional.of(approved));
    }

    /** A self-registered ally under review, owned by the account. */
    private void pendingAlly() {
        Ally ally = new Ally();
        ally.setId(30L);
        ally.setSegment(Segment.B2P);
        ally.setRegistrationChannel(Ally.RegistrationChannel.SELF_SERVICE);
        ally.setRegistrationStatus(Ally.RegistrationStatus.PENDING);
        ally.setCreatedAt(Instant.now());
        AllyUser owner = new AllyUser();
        owner.setAlly(ally);
        owner.setAllyRole(AllyUser.AllyRole.OWNER);
        lenient().when(allyUsers.findActiveByUserUuid(userUuid)).thenReturn(List.of(owner));
    }

    @Test
    void memberWhoRegistersAnAlly_onThePendingAllyRole_seesTheAllyRegistration() {
        approvedAffiliation();
        pendingAlly();

        var progress = service.get(userUuid, role(RoleNames.ALLY_PENDING));

        assertThat(progress.kind()).isEqualTo(RegistrationProgressService.KIND_ALLY);
        assertThat(progress.submitted()).isFalse();
    }

    @Test
    void sameAccount_onThePendingMemberRole_seesTheAffiliation() {
        AffiliationRequest draft = new AffiliationRequest();
        draft.setStatus(AffiliationRequest.Status.DRAFT.name());
        when(affiliationRequests.findFirstByUser_UuidOrderByCreatedAtDesc(userUuid)).thenReturn(Optional.of(draft));
        pendingAlly();

        var progress = service.get(userUuid, role(RoleNames.MEMBER_PENDING));

        assertThat(progress.kind()).isEqualTo(RegistrationProgressService.KIND_MEMBER);
    }

    @Test
    void pendingAllyRoleWithoutAnAlly_hasNothingToShow_neverTheAffiliation() {
        approvedAffiliation();

        var progress = service.get(userUuid, role(RoleNames.ALLY_PENDING));

        assertThat(progress.kind()).isNull();
    }

    @Test
    void anotherRole_keepsTheSingleSegmentRule_affiliationFirstThenAlly() {
        pendingAlly();
        when(affiliationRequests.findFirstByUser_UuidOrderByCreatedAtDesc(userUuid)).thenReturn(Optional.empty());

        assertThat(service.get(userUuid, role(RoleNames.MEMBER)).kind()).isEqualTo(RegistrationProgressService.KIND_ALLY);

        approvedAffiliation();
        assertThat(service.get(userUuid, null).kind()).isEqualTo(RegistrationProgressService.KIND_MEMBER);
    }
}
