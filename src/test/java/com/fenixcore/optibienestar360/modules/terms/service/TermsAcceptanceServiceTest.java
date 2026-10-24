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
import com.fenixcore.optibienestar360.modules.terms.dto.TermsAcceptRequest;
import com.fenixcore.optibienestar360.modules.terms.dto.TermsAcceptanceHistoryDto;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsAcceptance;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion;
import com.fenixcore.optibienestar360.modules.terms.entity.TermsVersion.TermType;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsAcceptanceRepository;
import com.fenixcore.optibienestar360.modules.terms.repository.TermsVersionRepository;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Audience rules of {@link TermsAcceptanceService#pendingForUser}: MEMBER
 * for every user, ADVISOR for users with an active, non-system advisor record
 * (any rank, whatever their roles), ALLY from the active agreements of the
 * user's allies, once per agreement — and {@code is_public} gates none of
 * them. Each pending text is rendered with the variables of the entity that
 * made it due, and stored as accepted (V189).
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
        lenient().when(termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementIsNull(anyLong(), anyLong()))
                .thenReturn(false);
        lenient().when(termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementId(anyLong(), anyLong(),
                anyLong())).thenReturn(false);
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
        when(termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementIsNull(user.getId(), memberTerms.getId())).thenReturn(true);
        when(termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementIsNull(user.getId(), advisorTerms.getId())).thenReturn(true);

        assertThat(service.pendingForUser(user.getUuid())).isEmpty();
        verify(variableResolver, never()).forMember(any());
        verify(variableResolver, never()).forAdvisor(any());
    }

    @Test
    void acceptedVersions_areNotPending() {
        when(termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementIsNull(user.getId(), memberTerms.getId())).thenReturn(true);

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
    void ally_sharedTemplateOfTwoAllies_isPendingOncePerAgreementWithEachAllysData() {
        Ally first = belongToAlly(50L, "Clínica Norte");
        Ally second = belongToAlly(51L, "Clínica Sur");
        TermsVersion template = version(20L, TermType.ALLY, false);
        AllyAgreement north = agreement(first, template);
        AllyAgreement south = agreement(second, template);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(south, north));
        when(variableResolver.forAlly(north)).thenReturn(Map.of("ally.name", "Clínica Norte"));
        when(variableResolver.forAlly(south)).thenReturn(Map.of("ally.name", "Clínica Sur"));
        when(renderer.render(eq(template.getContentMarkdown()), anyMap()))
                .thenAnswer(inv -> "Convenio con " + ((Map<?, ?>) inv.getArgument(1)).get("ally.name"));

        List<PendingTermDto> allyItems = service.pendingForUser(user.getUuid()).stream()
                .filter(p -> p.termType() == TermType.ALLY).toList();

        assertThat(allyItems).hasSize(2);
        assertThat(allyItems).extracting(PendingTermDto::termsVersionUuid).containsOnly(template.getUuid());
        assertThat(allyItems).extracting(PendingTermDto::allyName).containsExactly("Clínica Norte", "Clínica Sur");
        assertThat(allyItems).extracting(PendingTermDto::allyAgreementUuid)
                .containsExactly(north.getUuid(), south.getUuid());
        assertThat(allyItems).extracting(PendingTermDto::contentMarkdown)
                .containsExactly("Convenio con Clínica Norte", "Convenio con Clínica Sur");
    }

    @Test
    void ally_primaryAllyComesFirst() {
        Ally plain = belongToAlly(50L, "A Clínica");
        Ally primary = belongToAlly(51L, "Z Clínica");
        memberships.getLast().setPrimary(true);
        TermsVersion template = version(20L, TermType.ALLY, false);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(agreement(plain, template), agreement(primary, template)));

        assertThat(service.pendingForUser(user.getUuid())).filteredOn(p -> p.termType() == TermType.ALLY)
                .extracting(PendingTermDto::allyName).containsExactly("Z Clínica", "A Clínica");
    }

    @Test
    void ally_acceptedAgreement_leavesTheOtherAgreementOfTheSameTemplatePending() {
        Ally first = belongToAlly(50L, "Clínica Norte");
        Ally second = belongToAlly(51L, "Clínica Sur");
        TermsVersion template = version(20L, TermType.ALLY, false);
        AllyAgreement north = agreement(first, template);
        AllyAgreement south = agreement(second, template);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(north, south));
        when(termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementId(user.getId(),
                template.getId(), north.getId())).thenReturn(true);

        assertThat(service.pendingForUser(user.getUuid())).filteredOn(p -> p.termType() == TermType.ALLY)
                .extracting(PendingTermDto::allyAgreementUuid).containsExactly(south.getUuid());
    }

    @Test
    void accept_storesOneAcceptancePerAgreementWithItsRenderedTextAndHash() {
        Ally first = belongToAlly(50L, "Clínica Norte");
        Ally second = belongToAlly(51L, "Clínica Sur");
        TermsVersion template = version(20L, TermType.ALLY, false);
        AllyAgreement north = agreement(first, template);
        AllyAgreement south = agreement(second, template);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(north, south));
        when(variableResolver.forAlly(north)).thenReturn(Map.of("ally.name", "Clínica Norte"));
        when(renderer.render(eq(template.getContentMarkdown()), anyMap()))
                .thenAnswer(inv -> "Convenio con " + ((Map<?, ?>) inv.getArgument(1)).get("ally.name"));

        service.accept(user.getUuid(), List.of(new TermsAcceptRequest.Item(template.getUuid(), north.getUuid())), null);

        ArgumentCaptor<TermsAcceptance> saved = ArgumentCaptor.forClass(TermsAcceptance.class);
        verify(termsAcceptanceRepository, times(1)).save(saved.capture());
        TermsAcceptance acceptance = saved.getValue();
        assertThat(acceptance.getAllyAgreement()).isSameAs(north);
        assertThat(acceptance.getTermsVersion()).isSameAs(template);
        assertThat(acceptance.getChannel()).isEqualTo(TermsAcceptance.Channel.PORTAL);
        assertThat(acceptance.getRenderedContent()).isEqualTo("Convenio con Clínica Norte");
        assertThat(acceptance.getContentSha256()).isEqualTo(TermsAcceptanceService.sha256("Convenio con Clínica Norte"));
    }

    @Test
    void accept_bareVersionUuidSharedByTwoAgreements_requiresTheAgreement() {
        Ally first = belongToAlly(50L, "Clínica Norte");
        Ally second = belongToAlly(51L, "Clínica Sur");
        TermsVersion template = version(20L, TermType.ALLY, false);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(agreement(first, template), agreement(second, template)));
        UUID templateUuid = template.getUuid();

        assertThatThrownBy(() -> service.acceptVersions(user.getUuid(), List.of(templateUuid), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms_acceptance.agreement_required");
        verify(termsAcceptanceRepository, never()).save(any());
    }

    @Test
    void accept_bareVersionUuidOfASingleItem_isStillAccepted() {
        service.acceptVersions(user.getUuid(), List.of(memberTerms.getUuid()), null);

        ArgumentCaptor<TermsAcceptance> saved = ArgumentCaptor.forClass(TermsAcceptance.class);
        verify(termsAcceptanceRepository).save(saved.capture());
        assertThat(saved.getValue().getAllyAgreement()).isNull();
        assertThat(saved.getValue().getRenderedContent()).isEqualTo(memberTerms.getContentMarkdown());
    }

    @Test
    void pending_newVersionOfAnAcceptedAgreement_carriesThePreviousAcceptance() {
        Ally ally = belongToAlly(50L, "Clínica Norte");
        TermsVersion v1 = version(20L, TermType.ALLY, false);
        v1.setValidFrom(Instant.now().minus(30, ChronoUnit.DAYS));
        TermsVersion v2 = version(21L, TermType.ALLY, false);
        AllyAgreement linked = agreement(ally, v2);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(linked));
        lenient().when(termsVersionRepository.findByTermTypeOrderByValidFromDesc(TermType.ALLY)).thenReturn(List.of(v2, v1));
        TermsAcceptance previous = acceptance(v1, linked, "Texto aceptado de la versión 1");
        when(termsAcceptanceRepository.findFirstByUserIdAndAllyAgreementIdOrderByAcceptedAtDescIdDesc(user.getId(),
                linked.getId())).thenReturn(Optional.of(previous));

        PendingTermDto item = service.pendingForUser(user.getUuid()).stream()
                .filter(p -> p.termType() == TermType.ALLY).findFirst().orElseThrow();

        assertThat(item.versionNumber()).isEqualTo(2);
        assertThat(item.previousAcceptance()).isNotNull();
        assertThat(item.previousAcceptance().versionNumber()).isEqualTo(1);
        assertThat(item.previousAcceptance().termsVersionUuid()).isEqualTo(v1.getUuid());
        assertThat(item.previousAcceptance().acceptedAt()).isEqualTo(previous.getAcceptedAt());
        assertThat(item.previousAcceptance().renderedContent()).isEqualTo("Texto aceptado de la versión 1");
    }

    @Test
    void pending_previousAcceptanceFallsBackToTheSameAllyAndRerendersLegacyRows() {
        Ally ally = belongToAlly(50L, "Clínica Norte");
        TermsVersion v1 = version(20L, TermType.ALLY, false);
        v1.setContentMarkdown("Plantilla v1 {{ally.name}}");
        TermsVersion v2 = version(21L, TermType.ALLY, false);
        AllyAgreement oldAgreement = agreement(ally, v1);
        AllyAgreement renewed = agreement(ally, v2);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(renewed));
        TermsAcceptance legacy = acceptance(v1, oldAgreement, null);
        when(termsAcceptanceRepository.findFirstByUserIdAndAllyAgreementAllyIdOrderByAcceptedAtDescIdDesc(user.getId(),
                ally.getId())).thenReturn(Optional.of(legacy));
        Map<String, String> oldValues = Map.of("ally.name", "Clínica Norte");
        when(variableResolver.forAlly(oldAgreement)).thenReturn(oldValues);

        PendingTermDto item = service.pendingForUser(user.getUuid()).stream()
                .filter(p -> p.termType() == TermType.ALLY).findFirst().orElseThrow();

        assertThat(item.previousAcceptance()).isNotNull();
        assertThat(item.previousAcceptance().renderedContent()).isEqualTo("Plantilla v1 {{ally.name}}");
        verify(renderer).render("Plantilla v1 {{ally.name}}", oldValues);
    }

    @Test
    void pending_firstAcceptance_hasNoPreviousAcceptance() {
        assertThat(service.pendingForUser(user.getUuid())).singleElement()
                .satisfies(p -> assertThat(p.previousAcceptance()).isNull());
    }

    @Test
    void history_listsEveryAcceptanceWithAllyAndText() {
        Ally ally = new Ally();
        ally.setId(50L);
        ally.setUuid(UUID.randomUUID());
        ally.setName("Clínica Norte");
        TermsVersion allyTemplate = version(20L, TermType.ALLY, false);
        AllyAgreement linked = agreement(ally, allyTemplate);
        TermsAcceptance allyAcceptance = acceptance(allyTemplate, linked, "Convenio aceptado");
        TermsAcceptance memberAcceptance = acceptance(memberTerms, null, null);
        memberAcceptance.setChannel(TermsAcceptance.Channel.SELF_SERVICE);
        when(termsAcceptanceRepository.findByUserIdOrderByAcceptedAtDescIdDesc(user.getId()))
                .thenReturn(List.of(allyAcceptance, memberAcceptance));

        List<TermsAcceptanceHistoryDto> history = service.historyForUser(user.getUuid());

        assertThat(history).hasSize(2);
        TermsAcceptanceHistoryDto allyRow = history.getFirst();
        assertThat(allyRow.termType()).isEqualTo(TermType.ALLY);
        assertThat(allyRow.allyName()).isEqualTo("Clínica Norte");
        assertThat(allyRow.allyUuid()).isEqualTo(ally.getUuid());
        assertThat(allyRow.allyAgreementUuid()).isEqualTo(linked.getUuid());
        assertThat(allyRow.versionNumber()).isEqualTo(1);
        assertThat(allyRow.renderedContent()).isEqualTo("Convenio aceptado");
        assertThat(allyRow.contentReconstructed()).isFalse();
        TermsAcceptanceHistoryDto memberRow = history.get(1);
        assertThat(memberRow.allyName()).isNull();
        assertThat(memberRow.channel()).isEqualTo(TermsAcceptance.Channel.SELF_SERVICE);
        assertThat(memberRow.renderedContent()).isEqualTo(memberTerms.getContentMarkdown());
        assertThat(memberRow.contentReconstructed()).isTrue();
    }

    @Test
    void selfService_storesTheTextRenderedWithTheNewMembersData() {
        Map<String, String> memberValues = Map.of("member.fullName", "Ana");
        when(variableResolver.forMember(user)).thenReturn(memberValues);
        when(renderer.render(memberTerms.getContentMarkdown(), memberValues)).thenReturn("Contrato de Ana");

        service.recordSelfServiceAcceptance(user, memberTerms, null, null);

        ArgumentCaptor<TermsAcceptance> saved = ArgumentCaptor.forClass(TermsAcceptance.class);
        verify(termsAcceptanceRepository).save(saved.capture());
        assertThat(saved.getValue().getChannel()).isEqualTo(TermsAcceptance.Channel.SELF_SERVICE);
        assertThat(saved.getValue().getRenderedContent()).isEqualTo("Contrato de Ana");
        assertThat(saved.getValue().getContentSha256()).hasSize(64);
    }

    @Test
    void selfService_withTheShownText_storesItAsIs() {
        service.recordSelfServiceAcceptance(user, memberTerms, null, null, null, "Texto mostrado");

        ArgumentCaptor<TermsAcceptance> saved = ArgumentCaptor.forClass(TermsAcceptance.class);
        verify(termsAcceptanceRepository).save(saved.capture());
        assertThat(saved.getValue().getRenderedContent()).isEqualTo("Texto mostrado");
        verify(variableResolver, never()).forMember(any());
    }

    @Test
    void sha256_isTheHexDigestOfTheUtf8Text() {
        assertThat(TermsAcceptanceService.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(TermsAcceptanceService.sha256(null)).isNull();
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

        assertThatThrownBy(() -> service.acceptVersions(user.getUuid(), List.of(advisorTermsUuid), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms_acceptance.not_pending");
    }

    @Test
    void pending_onlyTheMemberAgreementIsMandatory() {
        beAdvisor(AdvisorRankCodes.ADVISOR, false);

        assertThat(service.pendingForUser(user.getUuid()))
                .extracting(PendingTermDto::termType, PendingTermDto::mandatory)
                .containsExactly(tuple(TermType.MEMBER, true),
                        tuple(TermType.ADVISOR, false));
    }

    @Test
    void hasPending_isTrueOnlyForTheKindsStillToAccept() {
        beAdvisor(AdvisorRankCodes.ADVISOR, false);
        when(termsAcceptanceRepository.existsByUserIdAndTermsVersionIdAndAllyAgreementIsNull(1L, advisorTerms.getId()))
                .thenReturn(true);

        assertThat(service.hasPending(user.getUuid(), TermType.MEMBER)).isTrue();
        assertThat(service.hasPending(user.getUuid(), TermType.ADVISOR)).isFalse();
        assertThat(service.hasPending(user.getUuid(), TermType.ALLY)).isFalse();
        verify(renderer, never()).render(anyString(), anyMap());
    }

    @Test
    void acceptOne_storesTheGivenAgreementOnly() {
        Ally first = belongToAlly(50L, "Clínica Norte");
        Ally second = belongToAlly(51L, "Clínica Sur");
        TermsVersion template = version(20L, TermType.ALLY, false);
        AllyAgreement north = agreement(first, template);
        AllyAgreement south = agreement(second, template);
        when(allyAgreementRepository.findInForceWithTermsVersionByAllyIds(anyCollection(), eq("ACTIVE")))
                .thenReturn(List.of(north, south));

        service.acceptOne(user.getUuid(), template.getUuid(), south.getUuid(), null);

        ArgumentCaptor<TermsAcceptance> saved = ArgumentCaptor.forClass(TermsAcceptance.class);
        verify(termsAcceptanceRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getAllyAgreement()).isSameAs(south);
    }

    @Test
    void acceptOne_rejectsAVersionThatIsNotPending() {
        UUID advisorTermsUuid = advisorTerms.getUuid();

        assertThatThrownBy(() -> service.acceptOne(user.getUuid(), advisorTermsUuid, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms_acceptance.not_pending");
        verify(termsAcceptanceRepository, never()).save(any());
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
        return belongToAlly(allyId, "Aliado " + allyId);
    }

    private Ally belongToAlly(long allyId, String name) {
        Ally ally = new Ally();
        ally.setId(allyId);
        ally.setUuid(UUID.randomUUID());
        ally.setName(name);
        AllyUser membership = new AllyUser();
        membership.setAlly(ally);
        membership.setUser(user);
        memberships.add(membership);
        return ally;
    }

    private static long nextAgreementId = 900L;

    private static AllyAgreement agreement(Ally ally, TermsVersion version) {
        AllyAgreement agreement = new AllyAgreement();
        agreement.setId(nextAgreementId++);
        agreement.setUuid(UUID.randomUUID());
        agreement.setAlly(ally);
        agreement.setTermsVersion(version);
        return agreement;
    }

    private TermsAcceptance acceptance(TermsVersion version, AllyAgreement agreement, String renderedContent) {
        TermsAcceptance acceptance = new TermsAcceptance();
        acceptance.setUuid(UUID.randomUUID());
        acceptance.setUser(user);
        acceptance.setTermsVersion(version);
        acceptance.setAllyAgreement(agreement);
        acceptance.setAcceptedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        acceptance.setRenderedContent(renderedContent);
        return acceptance;
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
