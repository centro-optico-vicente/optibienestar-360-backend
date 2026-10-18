package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutProfileDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutProfileUpsertRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterPayoutProfile;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterPayoutProfileRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * 1:1 payout profile of an advisor (V182, dossier section V), mounted at
 * {@code /v1/admin/promoters/{promoterUuid}/payout-profile} plus the
 * read-only self-service {@code GET /v1/promoter/me/payout-profile}.
 *
 * <p>Same contract as the member medical-record sub-resource (hub ADR 0012):
 * {@code GET} is always 200 with an {@code exists} flag when the promoter
 * exists (404 only for an unknown promoter), {@code PUT} upserts and
 * reactivates a soft-deleted row, {@code DELETE} soft-deletes.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoterPayoutProfileService {

    private final PromoterRepository promoterRepository;
    private final PromoterPayoutProfileRepository payoutProfileRepository;
    private final BankRepository bankRepository;

    public PromoterPayoutProfileDto getForPromoter(UUID promoterUuid) {
        return getFor(findPromoter(promoterUuid));
    }

    /** Self-service variant — resolves the promoter from the JWT-authenticated user. */
    public PromoterPayoutProfileDto getMine(UUID actorUserUuid) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        return getFor(promoter);
    }

    /**
     * Upsert with full-replace semantics (see {@link PromoterPayoutProfileUpsertRequest}):
     * creates on first call, otherwise overwrites every field — {@code null}
     * clears it. Reactivates a soft-deleted row.
     */
    @Transactional
    @Auditable(entity = "promoter_payout_profile", action = AuditAction.UPDATE)
    public PromoterPayoutProfileDto upsert(UUID promoterUuid, PromoterPayoutProfileUpsertRequest req) {
        Promoter promoter = findPromoter(promoterUuid);
        if (promoter.isSystem()) {
            throw new IllegalArgumentException("promoter.system.not_editable");
        }
        PromoterPayoutProfile profile = payoutProfileRepository.findByPromoterId(promoter.getId())
                .orElseGet(() -> {
                    PromoterPayoutProfile fresh = new PromoterPayoutProfile();
                    fresh.setPromoter(promoter);
                    return fresh;
                });
        profile.setActive(true);
        profile.setStatus("ACTIVE");

        profile.setBank(resolveBank(req.bankUuid()));
        profile.setAccountNumber(blankToNull(req.accountNumber()));
        profile.setAccountHolderDocumentType(blankToNull(req.accountHolderDocumentType()));
        profile.setAccountHolderDocumentNumber(blankToNull(req.accountHolderDocumentNumber()));
        profile.setMobilePaymentPhone(blankToNull(req.mobilePaymentPhone()));
        profile.setMobilePaymentBank(resolveBank(req.mobilePaymentBankUuid()));
        profile.setUbiiAccount(blankToNull(req.ubiiAccount()));
        profile.setUbiiHolderName(blankToNull(req.ubiiHolderName()));

        // Mirrors the V182 CHECKs for a clean 422 before the constraint fires.
        if (profile.getAccountNumber() != null && !profile.getAccountNumber().matches("^[0-9]{20}$")) {
            throw new IllegalArgumentException("promoter_payout_profile.account_number.format");
        }
        if ((profile.getAccountHolderDocumentType() == null) != (profile.getAccountHolderDocumentNumber() == null)) {
            throw new IllegalArgumentException("promoter_payout_profile.holder_document.incomplete");
        }

        return toDto(payoutProfileRepository.save(profile));
    }

    @Transactional
    @Auditable(entity = "promoter_payout_profile", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void delete(UUID promoterUuid) {
        Promoter promoter = findPromoter(promoterUuid);
        payoutProfileRepository.findByPromoterId(promoter.getId())
                .ifPresent(profile -> {
                    profile.setActive(false);
                    profile.setStatus("INACTIVE");
                });
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private PromoterPayoutProfileDto getFor(Promoter promoter) {
        return payoutProfileRepository.findByPromoterId(promoter.getId())
                .filter(PromoterPayoutProfile::isActive)  // soft-deleted rows look like "no profile yet"
                .map(PromoterPayoutProfileService::toDto)
                .orElseGet(() -> emptyDto(promoter));
    }

    private Promoter findPromoter(UUID uuid) {
        return promoterRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
    }

    private Bank resolveBank(UUID uuid) {
        if (uuid == null) return null;
        return bankRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("bank.not_found"));
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static PromoterPayoutProfileDto toDto(PromoterPayoutProfile p) {
        return new PromoterPayoutProfileDto(
                p.getUuid(),
                p.getPromoter().getUuid(),
                DisplayRefs.ref((Object) p.getBank()),
                p.getAccountNumber(),
                p.getAccountHolderDocumentType(),
                p.getAccountHolderDocumentNumber(),
                p.getMobilePaymentPhone(),
                DisplayRefs.ref((Object) p.getMobilePaymentBank()),
                p.getUbiiAccount(),
                p.getUbiiHolderName(),
                p.isActive(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                true);
    }

    /** Empty representation for "no payout profile yet" — every payout field {@code null}, {@code exists=false}. */
    private static PromoterPayoutProfileDto emptyDto(Promoter promoter) {
        return new PromoterPayoutProfileDto(
                null, promoter.getUuid(),
                null, null, null, null,
                null, null,
                null, null,
                true, null, null, null,
                false);
    }
}
