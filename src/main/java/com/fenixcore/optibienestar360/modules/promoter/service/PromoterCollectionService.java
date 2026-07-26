package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.CollectionScoreDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberContactDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterMemberRow;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterMemberContact;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterMemberContact.ContactType;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterMemberContactRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Delegated collection management for a promoter over their own portfolio
 * (v2 PDF 2.b). Backs the self-service {@code /v1/promoter/me/contacts} +
 * {@code /v1/promoter/me/collection-score} endpoints.
 *
 * <p>Every operation resolves the promoter from the JWT user and — for the
 * per-member ones — verifies the target member belongs to that promoter
 * ({@code member.promoter_id == currentPromoter.id}), so a promoter can only
 * log outreach against their own affiliates.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoterCollectionService {

    private final PromoterRepository promoterRepository;
    private final MemberRepository memberRepository;
    private final PromoterMemberContactRepository contactRepository;

    // ─── Writes ─────────────────────────────────────────────────────────────

    @Transactional
    public PromoterMemberContactDto registerReminder(UUID actorUserUuid, UUID memberUuid, String note) {
        Promoter promoter = resolvePromoter(actorUserUuid);
        Member member = ownedMember(promoter, memberUuid);
        return toDto(save(promoter, member, ContactType.REMINDER, note, null, null));
    }

    @Transactional
    public PromoterMemberContactDto registerPaymentPromise(UUID actorUserUuid, UUID memberUuid,
                                                           BigDecimal promisedAmount, LocalDate promisedAtDate,
                                                           String note) {
        Promoter promoter = resolvePromoter(actorUserUuid);
        Member member = ownedMember(promoter, memberUuid);
        return toDto(save(promoter, member, ContactType.PAYMENT_PROMISE, note, promisedAmount, promisedAtDate));
    }

    // ─── Reads ──────────────────────────────────────────────────────────────

    public List<PromoterMemberContactDto> listContacts(UUID actorUserUuid, UUID memberUuid) {
        Promoter promoter = resolvePromoter(actorUserUuid);
        Member member = ownedMember(promoter, memberUuid);
        return contactRepository
                .findByPromoterIdAndMemberIdOrderByCreatedAtDesc(promoter.getId(), member.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    /**
     * Share of the promoter's active portfolio that is up to date (ACTIVE
     * membership). Reuses the dashboard's portfolio query; {@code scorePct} is
     * 0.00 when the portfolio is empty.
     */
    public CollectionScoreDto collectionScore(UUID actorUserUuid) {
        Promoter promoter = resolvePromoter(actorUserUuid);
        List<PromoterMemberRow> portfolio = memberRepository.findPromoterPortfolio(promoter.getId());

        int upToDate = 0;
        int overdue = 0;
        int withoutMembership = 0;
        for (PromoterMemberRow row : portfolio) {
            String status = row.membershipStatus();
            if ("ACTIVE".equals(status)) {
                upToDate++;
            } else if ("SUSPENDED".equals(status) || "EXPIRED".equals(status)) {
                overdue++;
            } else {
                withoutMembership++;
            }
        }

        int total = portfolio.size();
        BigDecimal scorePct = total == 0
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.valueOf(upToDate)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

        return new CollectionScoreDto(promoter.getUuid(), total, upToDate, overdue, withoutMembership, scorePct);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Promoter resolvePromoter(UUID actorUserUuid) {
        return promoterRepository.findActiveByUserUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
    }

    /**
     * Resolves the member and verifies it belongs to the promoter's portfolio.
     * A member that doesn't exist OR isn't attributed to this promoter surfaces
     * the same 404 — a promoter can't probe affiliates outside their book.
     */
    private Member ownedMember(Promoter promoter, UUID memberUuid) {
        Member member = memberRepository.findByUuid(memberUuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        if (member.getPromoter() == null || !member.getPromoter().getId().equals(promoter.getId())) {
            throw new NoSuchElementException("promoter.member.not_in_portfolio");
        }
        return member;
    }

    private PromoterMemberContact save(Promoter promoter, Member member, ContactType type,
                                       String note, BigDecimal promisedAmount, LocalDate promisedAtDate) {
        PromoterMemberContact contact = new PromoterMemberContact();
        contact.setPromoter(promoter);
        contact.setMember(member);
        contact.setType(type);
        contact.setNote(note);
        contact.setPromisedAmount(promisedAmount);
        contact.setPromisedAtDate(promisedAtDate);
        return contactRepository.save(contact);
    }

    private PromoterMemberContactDto toDto(PromoterMemberContact c) {
        return new PromoterMemberContactDto(
                c.getUuid(),
                c.getMember() != null ? c.getMember().getUuid() : null,
                c.getType(),
                c.getNote(),
                c.getPromisedAmount(),
                c.getPromisedAtDate(),
                c.getCreatedAt());
    }
}
