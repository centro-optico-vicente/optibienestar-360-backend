package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutProfileDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutProfileUpsertRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterPayoutProfile;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterPayoutProfileRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Contract of the 1:1 payout-profile sub-resource (V182): empty state is a
 * 200 DTO with {@code exists=false} (never 404), 404 only for an unknown
 * promoter, and the service-side mirrors of the V182 CHECKs.
 */
@ExtendWith(MockitoExtension.class)
class PromoterPayoutProfileServiceTest {

    @Mock private PromoterRepository promoterRepository;
    @Mock private PromoterPayoutProfileRepository payoutProfileRepository;
    @Mock private BankRepository bankRepository;

    private PromoterPayoutProfileService service;
    private Promoter promoter;

    @BeforeEach
    void setup() {
        service = new PromoterPayoutProfileService(promoterRepository, payoutProfileRepository, bankRepository);
        promoter = new Promoter();
        promoter.setId(7L);
        promoter.setUuid(UUID.randomUUID());
        lenient().when(promoterRepository.findByUuid(promoter.getUuid())).thenReturn(Optional.of(promoter));
        lenient().when(payoutProfileRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void get_returnsEmptyDtoWithExistsFalse_whenNoRow() {
        when(payoutProfileRepository.findByPromoterId(7L)).thenReturn(Optional.empty());

        PromoterPayoutProfileDto dto = service.getForPromoter(promoter.getUuid());

        assertThat(dto.exists()).isFalse();
        assertThat(dto.uuid()).isNull();
        assertThat(dto.promoterUuid()).isEqualTo(promoter.getUuid());
        assertThat(dto.accountNumber()).isNull();
        assertThat(dto.bank()).isNull();
    }

    @Test
    void get_returnsEmptyDto_whenRowSoftDeleted() {
        PromoterPayoutProfile row = new PromoterPayoutProfile();
        row.setPromoter(promoter);
        row.setActive(false);
        when(payoutProfileRepository.findByPromoterId(7L)).thenReturn(Optional.of(row));

        assertThat(service.getForPromoter(promoter.getUuid()).exists()).isFalse();
    }

    @Test
    void get_unknownPromoter_is404() {
        UUID unknown = UUID.randomUUID();
        when(promoterRepository.findByUuid(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getForPromoter(unknown))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("promoter.not_found");
    }

    @Test
    void upsert_createsRow_andReturnsExistsTrue() {
        when(payoutProfileRepository.findByPromoterId(7L)).thenReturn(Optional.empty());

        PromoterPayoutProfileDto dto = service.upsert(promoter.getUuid(), request("01020123450000012345", "V", "12345678"));

        assertThat(dto.exists()).isTrue();
        assertThat(dto.accountNumber()).isEqualTo("01020123450000012345");
        assertThat(dto.accountHolderDocumentType()).isEqualTo("V");
    }

    @Test
    void upsert_rejectsAccountNumberThatIsNot20Digits() {
        when(payoutProfileRepository.findByPromoterId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(promoter.getUuid(), request("0102-0123", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_profile.account_number.format");
    }

    @Test
    void upsert_rejectsHalfHolderDocumentPair() {
        when(payoutProfileRepository.findByPromoterId(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.upsert(promoter.getUuid(), request(null, "V", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter_payout_profile.holder_document.incomplete");
    }

    @Test
    void upsert_rejectsSystemPromoter() {
        promoter.setSystem(true);

        assertThatThrownBy(() -> service.upsert(promoter.getUuid(), request(null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("promoter.system.not_editable");
    }

    private static PromoterPayoutProfileUpsertRequest request(String accountNumber, String docType, String docNumber) {
        return new PromoterPayoutProfileUpsertRequest(null, accountNumber, docType, docNumber,
                "+58 414 5550101", null, null, null);
    }
}
