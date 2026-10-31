package com.fenixcore.optibienestar360.modules.patientaccess.service;

import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.ally.entity.AllyUser;
import com.fenixcore.optibienestar360.modules.ally.repository.AllyUserRepository;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.entity.UserRole;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.member.dto.MedicalRecordDto;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.member.service.MedicalRecordService;
import com.fenixcore.optibienestar360.modules.patientaccess.dto.AllyPatientLinkDto;
import com.fenixcore.optibienestar360.modules.patientaccess.dto.MedicalRecordAccessDto;
import com.fenixcore.optibienestar360.modules.patientaccess.dto.MyMedicalRecordAccessDto;
import com.fenixcore.optibienestar360.modules.patientaccess.dto.PatientAccessEligibilityDto;
import com.fenixcore.optibienestar360.modules.patientaccess.entity.AllyPatientLink;
import com.fenixcore.optibienestar360.modules.patientaccess.entity.MedicalRecordAccessLog;
import com.fenixcore.optibienestar360.modules.patientaccess.repository.AllyPatientLinkRepository;
import com.fenixcore.optibienestar360.modules.patientaccess.repository.MedicalRecordAccessLogRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.system.service.SystemConfigService;
import com.fenixcore.optibienestar360.security.RoleNames;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * Medical allies read their patients' medical history (hub ADR 0032).
 *
 * <p><b>Who.</b> Only at an ally of segment B2P with a type of the MEDICAL
 * professional area, and only the ally's holder person (the doctor, V211)
 * or a user with the {@code OPERADOR_MEDICO} role linked to that ally.
 * Reception staff, B2B allies and wellness professionals never qualify. The
 * role is read from the database: the JWT carries permissions, not roles.</p>
 *
 * <p><b>When.</b> A QR scan of the member's credential opens a doctor–patient
 * link ({@link #openLink}); from then on the doctor reads the history from
 * any of their sites until the member revokes it. A typed document does not
 * open a link — the frontend only calls {@link #openLink} after a scan.</p>
 *
 * <p><b>Control.</b> Every read is logged; the member sees the links and the
 * reads and may revoke a link. Everything is behind the system switch
 * {@code system_configs.patient_medical_access_enabled} (OFF by default).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PatientAccessService {

    /** How many reads the member's trail returns. */
    static final int ACCESS_LOG_LIMIT = 100;

    private final AllyUserRepository allyUserRepository;
    private final UserRepository userRepository;
    private final MemberRepository memberRepository;
    private final AllyPatientLinkRepository linkRepository;
    private final MedicalRecordAccessLogRepository accessLogRepository;
    private final MedicalRecordService medicalRecordService;
    private final SystemConfigService systemConfigService;

    // ─── Ally side ────────────────────────────────────────────────────────────

    public PatientAccessEligibilityDto eligibility(UUID allyUuid, UUID actorUuid) {
        boolean enabled = systemConfigService.isPatientMedicalAccessEnabled();
        if (!enabled) {
            return new PatientAccessEligibilityDto(false, false);
        }
        AllyUser membership = allyUserRepository.findActiveByAllyUuidAndUserUuid(allyUuid, actorUuid).orElse(null);
        if (membership == null) {
            return new PatientAccessEligibilityDto(true, false);
        }
        User actor = userRepository.findWithRolesByUuid(actorUuid).orElse(null);
        return new PatientAccessEligibilityDto(true, actor != null && qualifies(membership.getAlly(), actor));
    }

    /**
     * Opens (or returns the already active) link between the ally's doctor and
     * the scanned member.
     */
    @Transactional
    public AllyPatientLinkDto openLink(UUID allyUuid, UUID memberUuid, UUID actorUuid) {
        Context ctx = requireDoctorContext(allyUuid, actorUuid);
        Member member = memberRepository.findByUuid(memberUuid)
                .filter(Member::isActive)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        AllyPatientLink link = linkRepository.findActive(ctx.doctor().getId(), member.getId())
                .orElseGet(() -> {
                    AllyPatientLink fresh = new AllyPatientLink();
                    fresh.setDoctorPerson(ctx.doctor());
                    fresh.setMember(member);
                    fresh.setCreatedAtAlly(ctx.ally());
                    fresh.setCreatedByUser(ctx.actor());
                    fresh.setStatus(AllyPatientLink.LinkStatus.ACTIVE.name());
                    return linkRepository.save(fresh);
                });
        return toDto(link);
    }

    /** "Mis pacientes": the doctor's active links, from any of their sites. */
    public Page<AllyPatientLinkDto> patients(UUID allyUuid, UUID actorUuid, Pageable pageable) {
        Context ctx = requireDoctorContext(allyUuid, actorUuid);
        return linkRepository.findActiveByDoctor(ctx.doctor().getId(), pageable).map(this::toDto);
    }

    /** Reads the patient's medical history (read-only) and logs the read. */
    @Transactional
    public MedicalRecordDto readMedicalRecord(UUID allyUuid, UUID memberUuid, UUID actorUuid) {
        Context ctx = requireDoctorContext(allyUuid, actorUuid);
        Member member = memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        AllyPatientLink link = linkRepository.findActive(ctx.doctor().getId(), member.getId())
                .orElseThrow(() -> new AccessDeniedException("patient_access.no_link"));

        MedicalRecordAccessLog read = new MedicalRecordAccessLog();
        read.setLink(link);
        read.setAccessedBy(ctx.actor());
        read.setAlly(ctx.ally());
        read.setMember(member);
        accessLogRepository.save(read);

        return medicalRecordService.getForMember(memberUuid);
    }

    // ─── Member side ──────────────────────────────────────────────────────────

    /** Who can read the caller's medical history, and who read it. */
    public MyMedicalRecordAccessDto myAccess(UUID actorUuid) {
        return memberRepository.findByUserUuid(actorUuid)
                .map(this::accessOf)
                .orElseGet(() -> new MyMedicalRecordAccessDto(List.of(), List.of()));
    }

    /** Staff audit of one member. */
    public MyMedicalRecordAccessDto accessOfMember(UUID memberUuid) {
        Member member = memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        return accessOf(member);
    }

    /** The member revokes a link; only their own, and only an active one. */
    @Transactional
    public AllyPatientLinkDto revoke(UUID linkUuid, UUID actorUuid) {
        Member member = memberRepository.findByUserUuid(actorUuid)
                .orElseThrow(() -> new NoSuchElementException("patient_link.not_found"));
        AllyPatientLink link = linkRepository.findByUuid(linkUuid)
                .filter(l -> Objects.equals(l.getMember().getId(), member.getId()))
                .orElseThrow(() -> new NoSuchElementException("patient_link.not_found"));
        if (link.isLinkActive()) {
            link.revoke(actorUuid);
        }
        return toDto(link);
    }

    // ─── Rules ────────────────────────────────────────────────────────────────

    /**
     * B2P ally of the MEDICAL area with a holder person, and the actor is that
     * person or holds the OPERADOR_MEDICO role. Needs {@code actor} loaded with
     * its roles ({@code findWithRolesByUuid}).
     */
    static boolean qualifies(Ally ally, User actor) {
        if (!isMedicalProfessionalAlly(ally) || ally.getHolderPerson() == null) {
            return false;
        }
        boolean isHolder = actor.getPerson() != null
                && Objects.equals(actor.getPerson().getId(), ally.getHolderPerson().getId());
        return isHolder || hasMedicalOperatorRole(actor);
    }

    static boolean isMedicalProfessionalAlly(Ally ally) {
        return ally.getSegment() == AllyType.Segment.B2P
                && ally.getAllyTypes().stream()
                        .anyMatch(t -> t.getProfessionalArea() == AllyType.ProfessionalArea.MEDICAL);
    }

    private static boolean hasMedicalOperatorRole(User actor) {
        return actor.getUserRoles() != null && actor.getUserRoles().stream()
                .filter(UserRole::isActive)
                .anyMatch(ur -> RoleNames.ALLY_MEDICAL_OPERATOR.equals(ur.getRole().getName()));
    }

    private Context requireDoctorContext(UUID allyUuid, UUID actorUuid) {
        if (!systemConfigService.isPatientMedicalAccessEnabled()) {
            throw new AccessDeniedException("patient_access.disabled");
        }
        AllyUser membership = allyUserRepository.findActiveByAllyUuidAndUserUuid(allyUuid, actorUuid)
                .orElseThrow(() -> new AccessDeniedException("patient_access.not_allowed"));
        User actor = userRepository.findWithRolesByUuid(actorUuid)
                .orElseThrow(() -> new AccessDeniedException("patient_access.not_allowed"));
        Ally ally = membership.getAlly();
        if (!qualifies(ally, actor)) {
            throw new AccessDeniedException("patient_access.not_allowed");
        }
        return new Context(ally, ally.getHolderPerson(), actor);
    }

    private MyMedicalRecordAccessDto accessOf(Member member) {
        List<AllyPatientLinkDto> links = linkRepository.findByMember(member.getId()).stream()
                .map(this::toDto)
                .toList();
        List<MedicalRecordAccessDto> reads = accessLogRepository
                .findByMember(member.getId(), PageRequest.of(0, ACCESS_LOG_LIMIT)).stream()
                .map(a -> new MedicalRecordAccessDto(a.getUuid(),
                        fullName(a.getLink().getDoctorPerson()),
                        a.getAlly().getUuid(), a.getAlly().getName(), a.getAccessedAt()))
                .toList();
        return new MyMedicalRecordAccessDto(links, reads);
    }

    private AllyPatientLinkDto toDto(AllyPatientLink link) {
        Member member = link.getMember();
        return new AllyPatientLinkDto(link.getUuid(),
                member.getUuid(), fullName(member.getPerson()),
                link.getDoctorPerson().getUuid(), fullName(link.getDoctorPerson()),
                link.getCreatedAtAlly().getUuid(), link.getCreatedAtAlly().getName(),
                link.getStatus(), link.getCreatedAt(), link.getRevokedAt());
    }

    private static String fullName(Person person) {
        return person == null ? null : person.getFullName();
    }

    private record Context(Ally ally, Person doctor, User actor) {}
}
