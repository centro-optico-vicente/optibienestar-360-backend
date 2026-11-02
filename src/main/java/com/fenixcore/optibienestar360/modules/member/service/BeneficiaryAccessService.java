package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.auth.dto.AdminCreateUserRequest;
import com.fenixcore.optibienestar360.modules.auth.dto.UserDto;
import com.fenixcore.optibienestar360.modules.auth.entity.Role;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.RoleRepository;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.auth.service.AuthService;
import com.fenixcore.optibienestar360.modules.auth.service.UserService;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryAccessRequest;
import com.fenixcore.optibienestar360.modules.member.dto.BeneficiaryDto;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.event.BeneficiaryAccessInvitedEvent;
import com.fenixcore.optibienestar360.modules.member.mapper.MemberMapper;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.security.RoleNames;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Beneficiary portal access (V195), driven by the holder from "Mi familia".
 *
 * <p>Granting creates an AFILIADO user on the beneficiary's own person, with
 * an unusable random password, and emails a link to set theirs — the same
 * setup-token mechanism as an assisted affiliation. The beneficiary row keeps
 * the link to that user; that is how the portal resolves the beneficiary's
 * context (their card on the holder's coverage, their own usages). Revoking
 * deactivates the user but keeps the link, so granting again reactivates the
 * same account and resends the invitation.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BeneficiaryAccessService {

    private final MemberRepository memberRepository;
    private final BeneficiaryRepository beneficiaryRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserService userService;
    private final AuthService authService;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final MemberMapper mapper;

    @Value("${app.affiliation-requests.activation-ttl-hours:72}")
    private long activationTtlHours = 72;

    @Transactional
    @Auditable(entity = "beneficiary", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public BeneficiaryDto grant(UUID holderUserUuid, UUID beneficiaryUuid, BeneficiaryAccessRequest request) {
        Member holder = holderMember(holderUserUuid);
        Beneficiary beneficiary = ownBeneficiary(holder, beneficiaryUuid);
        Person person = beneficiary.getPerson();

        String email = request != null && request.email() != null && !request.email().isBlank()
                ? request.email().trim().toLowerCase(Locale.ROOT)
                : person.getEmail();
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("beneficiary.access.email_required");
        }

        User user = beneficiary.getUser();
        if (user == null) {
            Role memberRole = roleRepository.findByName(RoleNames.MEMBER)
                    .orElseThrow(() -> new IllegalStateException("role " + RoleNames.MEMBER + " missing"));
            // 64 hex chars: BCrypt rejects inputs over 72 bytes
            String unusable = passwordEncoder.encode(UUID.randomUUID().toString().replace("-", "")
                    + UUID.randomUUID().toString().replace("-", ""));
            UserDto created = userService.createUserWithPasswordHash(new AdminCreateUserRequest(
                    email, person.getFirstName(), person.getMiddleName(), person.getLastName(),
                    person.getSecondLastName(), null, person.getDocumentType(), person.getDocumentNumber(),
                    null, null, person.getPhone(), List.of(memberRole.getUuid()), memberRole.getUuid()),
                    unusable, holderUserUuid);
            user = userRepository.findByUuid(created.uuid())
                    .orElseThrow(() -> new IllegalStateException("user just created not found"));
            beneficiary.setUser(user);
        } else {
            user.setActive(true);
        }
        if (person.getEmail() == null || person.getEmail().isBlank()) {
            person.setEmail(email);
        }

        String activationUrl = authService.issuePasswordSetupLink(user, Duration.ofHours(activationTtlHours),
                Locale.forLanguageTag("es"));
        beneficiary.setAccessInvitedAt(Instant.now());
        eventPublisher.publishEvent(new BeneficiaryAccessInvitedEvent(
                user.getEmail(), person.getFullName(), holder.getPerson().getFullName(), activationUrl));
        return mapper.toBeneficiaryDto(beneficiary, null);
    }

    @Transactional
    @Auditable(entity = "beneficiary", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public BeneficiaryDto revoke(UUID holderUserUuid, UUID beneficiaryUuid) {
        Beneficiary beneficiary = ownBeneficiary(holderMember(holderUserUuid), beneficiaryUuid);
        if (beneficiary.getUser() != null) {
            beneficiary.getUser().setActive(false);
        }
        return mapper.toBeneficiaryDto(beneficiary, null);
    }

    private Member holderMember(UUID userUuid) {
        return memberRepository.findByUserUuid(userUuid)
                .filter(Member::isActive)
                .orElseThrow(() -> new NoSuchElementException("me.member.not_enrolled"));
    }

    /** Active beneficiary of the holder's own member; anyone else's is a 404. */
    private Beneficiary ownBeneficiary(Member holder, UUID beneficiaryUuid) {
        return beneficiaryRepository.findByUuid(beneficiaryUuid)
                .filter(Beneficiary::isActive)
                .filter(b -> b.getMember().getId().equals(holder.getId()))
                .orElseThrow(() -> new NoSuchElementException("beneficiary.not_found"));
    }
}
