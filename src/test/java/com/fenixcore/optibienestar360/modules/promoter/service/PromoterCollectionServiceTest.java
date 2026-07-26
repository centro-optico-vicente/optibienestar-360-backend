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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PromoterCollectionService} — delegated collection
 * management (v2 PDF 2.b). Locks the ownership guard (a promoter only touches
 * their own affiliates), the contact-row shape per type, and the collection
 * score maths.
 */
@ExtendWith(MockitoExtension.class)
class PromoterCollectionServiceTest {

    @Mock private PromoterRepository promoterRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PromoterMemberContactRepository contactRepository;

    private PromoterCollectionService service() {
        return new PromoterCollectionService(promoterRepository, memberRepository, contactRepository);
    }

    private final UUID userUuid = UUID.randomUUID();

    @Test
    void registerReminder_savesReminder_forOwnedMember() {
        Promoter promoter = promoter(7L);
        Member member = member(promoter);
        stubResolve(promoter);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        stubSave();

        PromoterMemberContactDto dto = service().registerReminder(userUuid, member.getUuid(), "Llamado hoy");

        PromoterMemberContact row = captureSaved();
        assertThat(row.getType()).isEqualTo(ContactType.REMINDER);
        assertThat(row.getPromoter()).isSameAs(promoter);
        assertThat(row.getMember()).isSameAs(member);
        assertThat(row.getNote()).isEqualTo("Llamado hoy");
        assertThat(row.getPromisedAmount()).isNull();
        assertThat(row.getPromisedAtDate()).isNull();
        assertThat(dto.type()).isEqualTo(ContactType.REMINDER);
        assertThat(dto.memberUuid()).isEqualTo(member.getUuid());
    }

    @Test
    void registerPaymentPromise_savesPromiseWithAmountAndDate() {
        Promoter promoter = promoter(7L);
        Member member = member(promoter);
        stubResolve(promoter);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        stubSave();

        service().registerPaymentPromise(userUuid, member.getUuid(),
                new BigDecimal("25.00"), LocalDate.of(2026, 8, 15), "Promete pagar");

        PromoterMemberContact row = captureSaved();
        assertThat(row.getType()).isEqualTo(ContactType.PAYMENT_PROMISE);
        assertThat(row.getPromisedAmount()).isEqualByComparingTo("25.00");
        assertThat(row.getPromisedAtDate()).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    void register_404_whenMemberNotInPortfolio() {
        Promoter mine = promoter(7L);
        Member notMine = member(promoter(99L));   // attributed to a different promoter
        stubResolve(mine);
        when(memberRepository.findByUuid(notMine.getUuid())).thenReturn(Optional.of(notMine));

        assertThatThrownBy(() -> service().registerReminder(userUuid, notMine.getUuid(), "x"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("promoter.member.not_in_portfolio");
        verify(contactRepository, never()).save(any());
    }

    @Test
    void register_404_whenCallerIsNotAPromoter() {
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().registerReminder(userUuid, UUID.randomUUID(), "x"))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("me.promoter.not_found");
        verify(contactRepository, never()).save(any());
    }

    @Test
    void listContacts_returnsMappedHistory() {
        Promoter promoter = promoter(7L);
        Member member = member(promoter);
        stubResolve(promoter);
        when(memberRepository.findByUuid(member.getUuid())).thenReturn(Optional.of(member));
        PromoterMemberContact c = new PromoterMemberContact();
        c.setUuid(UUID.randomUUID());
        c.setPromoter(promoter);
        c.setMember(member);
        c.setType(ContactType.REMINDER);
        c.setNote("nota");
        c.setCreatedAt(Instant.parse("2026-07-25T12:00:00Z"));
        when(contactRepository.findByPromoterIdAndMemberIdOrderByCreatedAtDesc(7L, member.getId()))
                .thenReturn(List.of(c));

        List<PromoterMemberContactDto> history = service().listContacts(userUuid, member.getUuid());

        assertThat(history).hasSize(1);
        assertThat(history.getFirst().type()).isEqualTo(ContactType.REMINDER);
        assertThat(history.getFirst().note()).isEqualTo("nota");
    }

    @Test
    void collectionScore_computesUpToDatePercentage() {
        Promoter promoter = promoter(7L);
        stubResolve(promoter);
        when(memberRepository.findPromoterPortfolio(7L)).thenReturn(List.of(
                row("ACTIVE"), row("ACTIVE"), row("SUSPENDED"), row(null)));

        CollectionScoreDto score = service().collectionScore(userUuid);

        assertThat(score.activeAffiliates()).isEqualTo(4);
        assertThat(score.upToDate()).isEqualTo(2);
        assertThat(score.overdue()).isEqualTo(1);
        assertThat(score.withoutMembership()).isEqualTo(1);
        assertThat(score.scorePct()).isEqualByComparingTo("50.00");   // 2/4
    }

    @Test
    void collectionScore_isZero_whenPortfolioEmpty() {
        Promoter promoter = promoter(7L);
        stubResolve(promoter);
        when(memberRepository.findPromoterPortfolio(7L)).thenReturn(List.of());

        assertThat(service().collectionScore(userUuid).scorePct()).isEqualByComparingTo("0.00");
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private void stubResolve(Promoter promoter) {
        when(promoterRepository.findActiveByUserUuid(userUuid)).thenReturn(Optional.of(promoter));
    }

    private void stubSave() {
        when(contactRepository.save(any())).thenAnswer(inv -> {
            PromoterMemberContact c = inv.getArgument(0);
            c.setUuid(UUID.randomUUID());
            c.setCreatedAt(Instant.parse("2026-07-25T12:00:00Z"));
            return c;
        });
    }

    private PromoterMemberContact captureSaved() {
        ArgumentCaptor<PromoterMemberContact> captor = ArgumentCaptor.forClass(PromoterMemberContact.class);
        verify(contactRepository).save(captor.capture());
        return captor.getValue();
    }

    private Promoter promoter(long id) {
        Promoter p = new Promoter();
        p.setId(id);
        p.setUuid(UUID.randomUUID());
        p.setActive(true);
        return p;
    }

    private Member member(Promoter promoter) {
        Member m = new Member();
        m.setId(20L);
        m.setUuid(UUID.randomUUID());
        m.setPromoter(promoter);
        return m;
    }

    private PromoterMemberRow row(String status) {
        return new PromoterMemberRow(UUID.randomUUID(), "Afiliado", status, null, null);
    }
}
