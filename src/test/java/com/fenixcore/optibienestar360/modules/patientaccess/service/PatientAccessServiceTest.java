package com.fenixcore.optibienestar360.modules.patientaccess.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.member.dto.MedicalRecordDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.member.service.MedicalRecordService;
import com.fenixcore.optibienestar360.modules.patientaccess.entity.AllyPatientLink;
import com.fenixcore.optibienestar360.modules.patientaccess.entity.MedicalRecordAccessLog;
import com.fenixcore.optibienestar360.modules.patientaccess.repository.AllyPatientLinkRepository;
import com.fenixcore.optibienestar360.modules.patientaccess.repository.MedicalRecordAccessLogRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import com.fenixcore.optibienestar360.security.RoleNames;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.ArrayList;
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
 * Authorization matrix and read logging of {@link PatientAccessService}
 * (hub ADR 0032): only the holder person or an OPERADOR_MEDICO of a B2P
 * ally of the MEDICAL area, with an active doctor–patient link, reads the
 * history; every read is logged; the system switch gates it all.
 */
@ExtendWith(MockitoExtension.class)
class PatientAccessServiceTest {

    @Mock private AllyUserRepository allyUserRepository;
    @Mock private UserRepository userRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private AllyPatientLinkRepository linkRepository;
    @Mock private MedicalRecordAccessLogRepository accessLogRepository;
    @Mock private MedicalRecordService medicalRecordService;
    @Mock private SystemConfigService systemConfigService;

    private final UUID actorUuid = UUID.randomUUID();
    private final Person doctor = person(1L);
    private final Member member = member();

    private PatientAccessService sut() {
        return new PatientAccessService(allyUserRepository, userRepository, memberRepository, linkRepository,
                accessLogRepository, medicalRecordService, systemConfigService);
    }

    // ─── qualifies(): the eligibility matrix ─────────────────────────────────

    @Test
    void qualifies_holderOfMedicalProfessionalAlly() {
        assertThat(PatientAccessService.qualifies(medicalAlly(), user(doctor))).isTrue();
    }

    @Test
    void qualifies_medicalOperatorOfMedicalProfessionalAlly() {
        assertThat(PatientAccessService.qualifies(medicalAlly(), user(person(2L), RoleNames.ALLY_MEDICAL_OPERATOR)))
                .isTrue();
    }

    @Test
    void notQualified_staffWithoutMedicalOperatorRole() {
        assertThat(PatientAccessService.qualifies(medicalAlly(), user(person(2L), RoleNames.ALLY))).isFalse();
    }

    @Test
    void notQualified_commercialAlly() {
        Ally ally = ally(AllyType.Segment.B2B, null);
        assertThat(PatientAccessService.qualifies(ally, user(doctor))).isFalse();
    }

    @Test
    void notQualified_wellnessProfessional() {
        Ally ally = ally(AllyType.Segment.B2P, AllyType.ProfessionalArea.WELLNESS);
        assertThat(PatientAccessService.qualifies(ally, user(doctor))).isFalse();
    }

    @Test
    void notQualified_medicalAllyWithoutHolderPerson() {
        Ally ally = medicalAlly();
        ally.setHolderPerson(null);
        assertThat(PatientAccessService.qualifies(ally, user(person(2L), RoleNames.ALLY_MEDICAL_OPERATOR))).isFalse();
    }

    @Test
    void notQualified_inactiveMedicalOperatorRole() {
        User operator = user(person(2L), RoleNames.ALLY_MEDICAL_OPERATOR);
        operator.getUserRoles().getFirst().setActive(false);
        assertThat(PatientAccessService.qualifies(medicalAlly(), operator)).isFalse();
    }

    // ─── read: switch, link, logging ────────────────────────────────────────

    @Test
    void read_whenSwitchIsOff_isForbiddenAndNotLogged() {
        when(systemConfigService.isPatientMedicalAccessEnabled()).thenReturn(false);

        assertThatThrownBy(() -> sut().readMedicalRecord(UUID.randomUUID(), member.getUuid(), actorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("patient_access.disabled");
        verify(accessLogRepository, never()).save(any());
    }

    @Test
    void read_byStaffWithoutMedicalRole_isForbidden() {
        Ally ally = medicalAlly();
        givenEnabledAndMember(ally, user(person(2L), RoleNames.ALLY));

        assertThatThrownBy(() -> sut().readMedicalRecord(ally.getUuid(), member.getUuid(), actorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("patient_access.not_allowed");
        verify(accessLogRepository, never()).save(any());
    }

    @Test
    void read_withoutActiveLink_isForbidden() {
        Ally ally = medicalAlly();
        givenEnabledAndMember(ally, user(doctor));
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        // A revoked link — or one that belongs to another doctor — is not returned by findActive.
        when(linkRepository.findActive(doctor.getId(), member.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut().readMedicalRecord(ally.getUuid(), member.getUuid(), actorUuid))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessage("patient_access.no_link");
        verify(medicalRecordService, never()).getForMember(any());
        verify(accessLogRepository, never()).save(any());
    }

    @Test
    void read_withActiveLink_returnsTheRecordAndLogsTheRead() {
        Ally ally = medicalAlly();
        User actor = user(doctor);
        givenEnabledAndMember(ally, actor);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        AllyPatientLink link = link(ally);
        when(linkRepository.findActive(doctor.getId(), member.getId())).thenReturn(Optional.of(link));
        MedicalRecordDto record = new MedicalRecordDto(UUID.randomUUID(), member.getPerson().getUuid(),
                "O+", "Penicilina", null, null, null, null, null, null, true, "ACTIVE", null, null, true);
        when(medicalRecordService.getForMember(member.getUuid())).thenReturn(record);

        assertThat(sut().readMedicalRecord(ally.getUuid(), member.getUuid(), actorUuid)).isSameAs(record);

        ArgumentCaptor<MedicalRecordAccessLog> logged = ArgumentCaptor.forClass(MedicalRecordAccessLog.class);
        verify(accessLogRepository).save(logged.capture());
        assertThat(logged.getValue().getLink()).isSameAs(link);
        assertThat(logged.getValue().getAccessedBy()).isSameAs(actor);
        assertThat(logged.getValue().getAlly()).isSameAs(ally);
        assertThat(logged.getValue().getMember()).isSameAs(member);
    }

    // ─── links ──────────────────────────────────────────────────────────────

    @Test
    void openLink_bindsTheAllysDoctorNotTheOperator() {
        Ally ally = medicalAlly();
        User operator = user(person(2L), RoleNames.ALLY_MEDICAL_OPERATOR);
        givenEnabledAndMember(ally, operator);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        when(linkRepository.findActive(doctor.getId(), member.getId())).thenReturn(Optional.empty());
        when(linkRepository.save(any(AllyPatientLink.class))).thenAnswer(inv -> inv.getArgument(0));

        sut().openLink(ally.getUuid(), member.getUuid(), actorUuid);

        ArgumentCaptor<AllyPatientLink> saved = ArgumentCaptor.forClass(AllyPatientLink.class);
        verify(linkRepository).save(saved.capture());
        assertThat(saved.getValue().getDoctorPerson()).isSameAs(doctor);
        assertThat(saved.getValue().getCreatedByUser()).isSameAs(operator);
        assertThat(saved.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void revoke_someoneElsesLink_isNotFound() {
        Member caller = member();
        caller.setId(99L);
        when(memberRepository.findByUserUuid(actorUuid)).thenReturn(Optional.of(caller));
        AllyPatientLink link = link(medicalAlly());
        when(linkRepository.findByUuid(link.getUuid())).thenReturn(Optional.of(link));

        assertThatThrownBy(() -> sut().revoke(link.getUuid(), actorUuid))
                .isInstanceOf(NoSuchElementException.class);
        assertThat(link.isLinkActive()).isTrue();
    }

    @Test
    void revoke_ownLink_marksItRevoked() {
        when(memberRepository.findByUserUuid(actorUuid)).thenReturn(Optional.of(member));
        AllyPatientLink link = link(medicalAlly());
        when(linkRepository.findByUuid(link.getUuid())).thenReturn(Optional.of(link));

        sut().revoke(link.getUuid(), actorUuid);

        assertThat(link.isLinkActive()).isFalse();
        assertThat(link.getRevokedAt()).isNotNull();
        assertThat(link.getRevokedBy()).isEqualTo(actorUuid);
    }

    // ─── fixtures ───────────────────────────────────────────────────────────

    private void givenEnabledAndMember(Ally ally, User actor) {
        when(systemConfigService.isPatientMedicalAccessEnabled()).thenReturn(true);
        AllyUser membership = new AllyUser();
        membership.setAlly(ally);
        membership.setUser(actor);
        when(allyUserRepository.findActiveByAllyUuidAndUserUuid(ally.getUuid(), actorUuid))
                .thenReturn(Optional.of(membership));
        when(userRepository.findWithRolesByUuid(actorUuid)).thenReturn(Optional.of(actor));
    }

    private Ally medicalAlly() {
        return ally(AllyType.Segment.B2P, AllyType.ProfessionalArea.MEDICAL);
    }

    private Ally ally(AllyType.Segment segment, AllyType.ProfessionalArea area) {
        AllyType type = new AllyType();
        type.setSegment(segment);
        type.setProfessionalArea(area);
        Ally ally = new Ally();
        ally.setId(10L);
        ally.setUuid(UUID.randomUUID());
        ally.setName("Consultorio Dr. Pérez");
        ally.setSegment(segment);
        ally.setAllyTypes(Set.of(type));
        ally.setHolderPerson(doctor);
        return ally;
    }

    private AllyPatientLink link(Ally ally) {
        AllyPatientLink link = new AllyPatientLink();
        link.setUuid(UUID.randomUUID());
        link.setDoctorPerson(doctor);
        link.setMember(member);
        link.setCreatedAtAlly(ally);
        link.setStatus(AllyPatientLink.LinkStatus.ACTIVE.name());
        return link;
    }

    private static User user(Person person, String... roleNames) {
        User user = new User();
        user.setUuid(UUID.randomUUID());
        user.setPerson(person);
        List<UserRole> roles = new ArrayList<>();
        for (String name : roleNames) {
            Role role = new Role();
            role.setName(name);
            UserRole ur = new UserRole();
            ur.setRole(role);
            ur.setUser(user);
            ur.setActive(true);
            roles.add(ur);
        }
        user.setUserRoles(roles);
        return user;
    }

    private static Person person(Long id) {
        Person p = new Person();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        return p;
    }

    private Member member() {
        Member m = new Member();
        m.setId(5L);
        m.setUuid(UUID.randomUUID());
        m.setPerson(person(3L));
        return m;
    }
}
