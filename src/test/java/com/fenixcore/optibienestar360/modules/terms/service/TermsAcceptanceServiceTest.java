package com.fenixcore.optibienestar360.modules.terms.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyAgreement;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyAgreementRepository;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.AdvisorRankCodes;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterRank;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.PromoterResolver;
import com.fenixcore.optibienestar360.modules.terms.dto.PendingTermDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsAcceptanceRepository;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Audience rules of {@link TermsAcceptanceService#pendingForUser}: MEMBER
 * for every user, ADVISOR for users with an active, non-system advisor record
 * (any rank, whatever their roles), ALLY from the active agreements of the
 * user's allies — and {@code is_public} gates none of them. Each pending text
 * is rendered with the variables of the entity that made it due.
 */
@ExtendWith(MockitoExtension.class)
class TermsAcceptanceServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private TermsVersionRepository termsVersionRepository;
    @Mock private TermsAcceptanceRepository termsAcceptanceRepository;
    @Mock private AllyUserRepository allyUserRepository;
    @Mock private AllyAgreementRepository allyAgreementRepository;
    @Mock private PromoterRepository promoterRepository;
    @Mock private TermsTemplateRenderer renderer;
    @Mock private TermsVariableResolver variableResolver;

    private TermsAcceptanceService service;
    private User user;
    private final List<AllyUser> memberships = new ArrayList<>();

    private final TermsVersion memberTerms = version(10L, TermType.MEMBER, false);
    private final TermsVersion advisorTerms = version(11L, TermType.ADVISOR, false);

    @BeforeEach
    void setup() {
        service = new TermsAcceptanceService(userRepository, termsVersionRepository, termsAcceptanceRepository,
                allyUserRepository, allyAgreementRepository, promoterRepository, renderer, variableResolver);
        user = new User();
        user.setId(1L);
        user.setUuid(UUID.randomUUID());
        lenient().when(userRepository.findByUuid(user.getUuid())).thenReturn(Optional.of(user));
        lenient().when(renderer.render(anyString(), anyMap())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(promoterRepository.findActiveByUserUuid(user.getUuid())).thenReturn(Optional.empty());
        lenient().when(variableResolver.forMember(any())).thenReturn(Map.of());
        lenient().when(variableResolver.forAdvisor(any())).thenReturn(Map.of());
        lenient().when(variableResolver.forAlly(any())).thenReturn(Map.of());
        lenient().when(termsAcceptanceRepository.existsByUserIdAndTermsVersionId(anyLong(), anyLong())).thenReturn(false);
        lenient().when(allyUserRepository.findActiveByUserUuid(user.getUuid())).thenReturn(memberships);
        stubCurrent(TermType.MEMBER, memberTerms);
        stubCurrent(TermType.ADVISOR, advisorTerms);
    }

    @Test
    void member_isPendingForAnAdminWithoutTheAffiliateRole() {
        grantRole(RoleNames.ADMINISTRATOR);

        assertThat(pendingTypes()).containsExactly(TermType.MEMBER);
    }

    @Test
    void member_isPendingForAUserWithNoRoleAtAll() {
        assertThat(pendingTypes()).containsExactly(TermType.MEMBER);
    }

    @Test
    void nonPublicVersions_areStillPending() {
        beAdvisor(AdvisorRankCodes.ADVISOR, false);

        List<PendingTermDto> pending = service.pendingForUser(user.getUuid());

        assertThat(memberTerms.isPublic()).isFalse();
        assertThat(pending).extracting(PendingTermDto::termsVersionUuid)
                .containsExactly(memberTerms.getUuid(), advisorTerms.getUuid());
    }

    @Test
    void advisor_isPendingForAnActiveAdvisorRecordOfAnyRank() {
        for (String rank : List.of(AdvisorRankCodes.ADVISOR, AdvisorRankCodes.SUPERVISOR, AdvisorRankCodes.COORDINATOR)) {
            beAdvisor(rank, false);

            assertThat(pendingTypes()).as("rank %s", rank).containsExactly(TermType.MEMBER, TermType.ADVISOR);
        }
    }

    @Test
    void advisor_isPendingEvenWithoutTheAdvisorRole() {
        grantRole(RoleNames.ADMINISTRATOR);
        beAdvisor(AdvisorRankCodes.SUPERVISOR, false);

        assertThat(pendingTypes()).containsExactly(TermType.MEMBER, TermType.ADVISOR);
    }

    @Test
    void advisor_isNotPendingForTheSystemAdvisor() {
        Promoter institution = beAdvisor(AdvisorRankCodes.ADVISOR, true);
        institution.setReferralCode(PromoterResolver.SYSTEM_ADVISOR_CODE);

        assertThat(pendingTypes()).containsExactly(TermType.MEMBER);
    }

    @Test
    void advisor_isNotPendingWithTheRoleButNoAdvisorRecord() {
        grantRole(RoleNames.ADVISOR);

        assertThat(pendingTypes()).containsExactly(TermType.MEMBER);
    }

    @Test
    void eachPendingText_isRenderedWithItsEntityVariables() {
        Promoter advisor = beAdvisor(AdvisorRankCodes.COORDINATOR, false);
        Map<String, String> memberValues = Map.of("member.fullName", "Ana");
        Map<String, String> advisorValues = Map.of("advisor.code", "ASE001");
        when(variableResolver.forMember(user)).thenReturn(memberValues);
        when(variableResolver.forAdvisor(advisor)).thenReturn(advisorValues);

        service.pendingForUser(user.getUuid());

        verify(renderer).render(memberTerms.getContentMarkdown(), memberValues);
        verify(renderer).render(advisorTerms.getContentMarkdown(), advisorValues);
    }

    @Test
    void acceptedVersions_skipTheVariableLookup() {
        beAdvisor(AdvisorRankCodes.ADVISOR, false);
        when(termsAcceptanceRepository.existsByUserIdAndTermsVersionId(user.getId(), memberTerms.getId())).thenReturn(true);
        when(termsAcceptanceRepository.existsByUserIdAndTermsVersionId(user.getId(), advisorTerms.getId())).thenReturn(true);

        assertThat(service.pendingForUser(user.getUuid())).isEmpty();
        verify(variableResolver, never()).forMember(any());
        verify(variableResolver, never()).forAdvisor(any());
    }

    @Test
    void acceptedVersions_areNotPending() {
        when(termsAcceptanceRepository.existsByUserIdAndTermsVersionId(user.getId(), memberTerms.getId())).thenReturn(true);

        assertThat(service.pendingForUser(user.getUuid())).isEmpty();
    }

    @Test
    void ally_comesFromTheTemplateLinkedToTheAllysActiveAgreement() {
        Ally ally = belongToAlly(50L);
        TermsVersion template = version(20L, TermType.ALLY, false);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(List.of(ally.getId()), "ACTIVE"))
                .thenReturn(List.of(agreement(ally, template), agreement(ally, null)));

        AllyAgreement linked = agreement(ally, template);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(List.of(ally.getId()), "ACTIVE"))
                .thenReturn(List.of(linked, agreement(ally, null)));
        Map<String, String> allyValues = Map.of("ally.name", "Clínica");
        when(variableResolver.forAlly(linked)).thenReturn(allyValues);

        List<PendingTermDto> pending = service.pendingForUser(user.getUuid());

        assertThat(pending).extracting(PendingTermDto::termsVersionUuid)
                .containsExactly(memberTerms.getUuid(), template.getUuid());
        verify(renderer).render(template.getContentMarkdown(), allyValues);
    }

    @Test
    void ally_sharedTemplateOfTwoAllies_isPendingOnce() {
        Ally first = belongToAlly(50L);
        Ally second = belongToAlly(51L);
        TermsVersion template = version(20L, TermType.ALLY, false);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(agreement(first, template), agreement(second, template)));

        assertThat(pendingTypes()).containsExactly(TermType.MEMBER, TermType.ALLY);
    }

    @Test
    void ally_notYetVigentLinkedTemplate_isNotPending() {
        Ally ally = belongToAlly(50L);
        TermsVersion scheduled = version(20L, TermType.ALLY, false);
        scheduled.setValidFrom(Instant.now().plus(5, ChronoUnit.DAYS));
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(agreement(ally, scheduled)));

        assertThat(pendingTypes()).containsExactly(TermType.MEMBER);
    }

    @Test
    void ally_userWithoutAllies_doesNotQueryAgreements() {
        service.pendingForUser(user.getUuid());

        verify(allyAgreementRepository, never()).findInForceWithTermsVersionByAllyIds(anyCollection(), any());
    }

    @Test
    void accept_rejectsAVersionThatIsNotPending() {
        UUID advisorTermsUuid = advisorTerms.getUuid();

        assertThatThrownBy(() -> service.accept(user.getUuid(), List.of(advisorTermsUuid), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms_acceptance.not_pending");
    }

    // ─── Fixtures ───────────────────────────────────────────────────────────

    private List<TermType> pendingTypes() {
        return service.pendingForUser(user.getUuid()).stream().map(PendingTermDto::termType).toList();
    }

    private void stubCurrent(TermType type, TermsVersion version) {
        lenient().when(termsVersionRepository
                        .findFirstByTermTypeAndAllyTypeIsNullAndSegmentIsNullAndAllyIsNullAndValidFromLessThanEqualOrderByValidFromDesc(
                                eq(type), any(Instant.class)))
                .thenReturn(Optional.of(version));
    }

    private UserRole grantRole(String roleName) {
        Role role = new Role();
        role.setName(roleName);
        UserRole userRole = new UserRole();
        userRole.setUser(user);
        userRole.setRole(role);
        user.getUserRoles().add(userRole);
        return userRole;
    }

    /** Gives the user an active advisor record of {@code rankCode} ({@code system} = the INSTITUCION kind). */
    private Promoter beAdvisor(String rankCode, boolean system) {
        PromoterRank rank = new PromoterRank();
        rank.setCode(rankCode);
        Promoter advisor = new Promoter();
        advisor.setId(70L);
        advisor.setUser(user);
        advisor.setRank(rank);
        advisor.setSystem(system);
        advisor.setReferralCode("ASE001");
        when(promoterRepository.findActiveByUserUuid(user.getUuid())).thenReturn(Optional.of(advisor));
        return advisor;
    }

    private Ally belongToAlly(long allyId) {
        Ally ally = new Ally();
        ally.setId(allyId);
        ally.setUuid(UUID.randomUUID());
        AllyUser membership = new AllyUser();
        membership.setAlly(ally);
        membership.setUser(user);
        memberships.add(membership);
        return ally;
    }

    private static AllyAgreement agreement(Ally ally, TermsVersion version) {
        AllyAgreement agreement = new AllyAgreement();
        agreement.setAlly(ally);
        agreement.setTermsVersion(version);
        return agreement;
    }

    private static TermsVersion version(long id, TermType type, boolean isPublic) {
        TermsVersion version = new TermsVersion();
        version.setId(id);
        version.setUuid(UUID.randomUUID());
        version.setTermType(type);
        version.setTitle(type.name() + " v1");
        version.setContentMarkdown("Contenido " + type.name());
        version.setPublic(isPublic);
        version.setValidFrom(Instant.now().minus(1, ChronoUnit.DAYS));
        return version;
    }
}
