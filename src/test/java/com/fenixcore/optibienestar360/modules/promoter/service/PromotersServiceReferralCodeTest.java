package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.catalog.repository.PromoterTypeRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterCreateRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.mapper.PromoterMapper;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the referral-code resolution added to {@link PromotersService}
 * (v2 PDF #4 "el promotor genera su código único"): blank input auto-generates a
 * short unique tag; a vanity code is validated for cross-table uniqueness.
 */
@ExtendWith(MockitoExtension.class)
class PromotersServiceReferralCodeTest {

    @Mock private PromoterRepository repository;
    @Mock private UserRepository userRepository;
    @Mock private MemberRepository memberRepository;
    @Mock private PromoterTypeRepository promoterTypeRepository;
    @Mock private PromoterMapper mapper;

    private PromotersService service;

    private final UUID userUuid = UUID.randomUUID();
    private final UUID personUuid = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new PromotersService(repository, userRepository, memberRepository, promoterTypeRepository, mapper);
        Person person = new Person();
        person.setId(1L);
        person.setUuid(personUuid);
        User user = new User();
        user.setId(1L);
        user.setUuid(userUuid);
        user.setPerson(person);
        lenient().when(userRepository.findByUuid(userUuid)).thenReturn(Optional.of(user));
        lenient().when(repository.existsByUserId(1L)).thenReturn(false);
        lenient().when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private PromoterCreateRequest request(String referralCode) {
        return new PromoterCreateRequest("Ana Ventas", null, referralCode,
                userUuid, "ana@example.com", "+58 412 5550100", null);
    }

    @Test
    void autoGeneratesShortCode_whenReferralCodeBlank() {
        when(repository.existsByReferralCode(anyString())).thenReturn(false);
        when(memberRepository.findByReferralCode(anyString())).thenReturn(Optional.empty());

        service.create(request("   "));   // blank → auto-generate

        Promoter saved = capturePromoter();
        // 6-char code from the print-safe alphabet (no 0/O/1/I/L).
        assertThat(saved.getReferralCode()).matches("^[23456789ABCDEFGHJKMNPQRSTUVWXYZ]{6}$");
    }

    @Test
    void keepsVanityCode_afterUniquenessChecks() {
        when(repository.existsByReferralCode("ANAV42")).thenReturn(false);
        when(memberRepository.findByReferralCode("ANAV42")).thenReturn(Optional.empty());

        service.create(request("anav42"));   // normalized to UPPER

        assertThat(capturePromoter().getReferralCode()).isEqualTo("ANAV42");
    }

    @Test
    void rejects_whenVanityCodeCollidesWithPromoter() {
        when(repository.existsByReferralCode("ANAV42")).thenReturn(true);

        assertThatThrownBy(() -> service.create(request("ANAV42")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter.referral_code.duplicate");
    }

    @Test
    void rejects_whenVanityCodeCollidesWithMember() {
        when(repository.existsByReferralCode("ANAV42")).thenReturn(false);
        when(memberRepository.findByReferralCode("ANAV42")).thenReturn(Optional.of(new Member()));

        assertThatThrownBy(() -> service.create(request("ANAV42")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter.referral_code.duplicate.member");
    }

    @Test
    void rejects_whenAutoGenerationExhausted() {
        // Every candidate collides → retries exhausted → clean 422.
        when(repository.existsByReferralCode(anyString())).thenReturn(true);

        assertThatThrownBy(() -> service.create(request(null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter.referral_code.generation_exhausted");
    }

    private Promoter capturePromoter() {
        ArgumentCaptor<Promoter> captor = ArgumentCaptor.forClass(Promoter.class);
        org.mockito.Mockito.verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
