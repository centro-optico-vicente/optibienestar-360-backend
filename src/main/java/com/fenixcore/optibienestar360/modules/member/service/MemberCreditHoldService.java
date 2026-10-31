package com.fenixcore.optibienestar360.modules.member.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.modules.member.dto.CreditHoldRequest;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Staff freeze / release of a person's whole credit (V205): while frozen nothing of it is applied automatically
 * (staff can still assign by hand). Freezing needs a reason and records who and when; releasing clears all three.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberCreditHoldService {

    private final MemberRepository memberRepository;

    @Transactional
    @Auditable(entity = "member", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public void setHold(UUID memberUuid, CreditHoldRequest request, UUID actorUserUuid) {
        Member member = memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        Person person = member.getPerson();
        if (Boolean.TRUE.equals(request.hold())) {
            if (request.reason() == null || request.reason().isBlank()) {
                throw new IllegalArgumentException("member.credit_hold.reason_required");
            }
            person.setCreditAutoApply(false);
            person.setCreditHoldReason(request.reason().trim());
            person.setCreditHoldBy(actorUserUuid);
            person.setCreditHoldAt(Instant.now());
        } else {
            person.setCreditAutoApply(true);
            person.setCreditHoldReason(null);
            person.setCreditHoldBy(null);
            person.setCreditHoldAt(null);
        }
    }
}
