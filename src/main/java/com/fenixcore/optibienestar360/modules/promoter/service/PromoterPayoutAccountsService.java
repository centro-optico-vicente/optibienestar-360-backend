package com.fenixcore.optibienestar360.modules.promoter.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.core.display.DisplayRefs;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutAccountDto;
import com.fenixcore.optibienestar360.modules.promoter.dto.PromoterPayoutAccountRequest;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.entity.PromoterPayoutAccount;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterPayoutAccountRepository;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Advisor payout accounts (V184, dossier section V), mounted at
 * {@code /v1/admin/promoters/{promoterUuid}/payout-accounts} plus the
 * read-only self-service {@code GET /v1/promoter/me/payout-accounts}.
 *
 * <p>Same sub-resource shape as {@link PromoterReferencesService}: every
 * read/write resolves the parent from the path and verifies the child belongs
 * to it (404 on URL tampering); delete is a soft-delete. Rules specific to
 * this resource:</p>
 * <ul>
 *   <li><b>Per-method validation</b> — the required fields come from the
 *       payment method's {@code is_mandatory_*} flags, never from hardcoded
 *       method codes, so a new method added to the catalog works without a
 *       code change.</li>
 *   <li><b>Single default</b> — the first account of an advisor becomes the
 *       default; setting a new default unsets the previous one in the same
 *       transaction (flushed first so the V184 partial unique index never
 *       sees two defaults).</li>
 *   <li>The INSTITUCION system promoter is not writable (422).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromoterPayoutAccountsService {

    /** Default first, then oldest first. */
    static final Sort DEFAULT_SORT = Sort.by(Sort.Order.desc("defaultAccount"), Sort.Order.asc("createdAt"));

    private static final String VE_ACCOUNT_NUMBER = "^[0-9]{20}$";

    private final PromoterRepository promoterRepository;
    private final PromoterPayoutAccountRepository accountRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final BankRepository bankRepository;

    public List<PromoterPayoutAccountDto> listForPromoter(UUID promoterUuid) {
        return listFor(findPromoter(promoterUuid));
    }

    /** Self-service variant — only the caller's own accounts, resolved from the JWT user. */
    public List<PromoterPayoutAccountDto> listMine(UUID actorUserUuid) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        return listFor(promoter);
    }

    public PromoterPayoutAccountDto get(UUID promoterUuid, UUID accountUuid) {
        return toDto(findUnderPromoter(promoterUuid, accountUuid));
    }

    @Transactional
    @Auditable(entity = "promoter_payout_account", action = AuditAction.CREATE)
    public PromoterPayoutAccountDto create(UUID promoterUuid, PromoterPayoutAccountRequest req) {
        Promoter promoter = findPromoter(promoterUuid);
        ensureNotSystemRow(promoter);

        PromoterPayoutAccount account = new PromoterPayoutAccount();
        account.setPromoter(promoter);
        apply(account, req);
        validate(account);

        boolean first = !accountRepository.existsByPromoterIdAndActiveTrue(promoter.getId());
        boolean makeDefault = first || Boolean.TRUE.equals(req.isDefault());
        if (makeDefault) {
            unsetCurrentDefaults(promoter.getId(), null);
        }
        account.setDefaultAccount(makeDefault);

        return toDto(accountRepository.save(account));
    }

    @Transactional
    @Auditable(entity = "promoter_payout_account", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public PromoterPayoutAccountDto update(UUID promoterUuid, UUID accountUuid, PromoterPayoutAccountRequest req) {
        PromoterPayoutAccount account = findUnderPromoter(promoterUuid, accountUuid);
        ensureNotSystemRow(account.getPromoter());

        apply(account, req);
        validate(account);

        return toDto(account);  // managed → dirty-check on commit
    }

    /** Marks the account as the advisor's default, unsetting the previous one atomically. */
    @Transactional
    @Auditable(entity = "promoter_payout_account", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public PromoterPayoutAccountDto setDefault(UUID promoterUuid, UUID accountUuid) {
        PromoterPayoutAccount account = findUnderPromoter(promoterUuid, accountUuid);
        ensureNotSystemRow(account.getPromoter());

        if (!account.isDefaultAccount()) {
            unsetCurrentDefaults(account.getPromoter().getId(), account.getId());
            account.setDefaultAccount(true);
        }
        return toDto(account);
    }

    /**
     * Soft-delete. A deleted default stops being the default; no other
     * account is promoted automatically — the operator picks the next one.
     */
    @Transactional
    @Auditable(entity = "promoter_payout_account", action = AuditAction.DELETE, uuidArgIndex = 1)
    public void delete(UUID promoterUuid, UUID accountUuid) {
        PromoterPayoutAccount account = findUnderPromoter(promoterUuid, accountUuid);
        ensureNotSystemRow(account.getPromoter());
        account.setActive(false);
        account.setStatus("INACTIVE");
        account.setDefaultAccount(false);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private List<PromoterPayoutAccountDto> listFor(Promoter promoter) {
        return accountRepository.findByPromoterIdAndActiveTrue(promoter.getId(), DEFAULT_SORT).stream()
                .map(PromoterPayoutAccountsService::toDto)
                .toList();
    }

    /**
     * Unsets every current default of the promoter except {@code keepId} and
     * flushes immediately, so the later {@code is_default = TRUE} write never
     * coexists with the old one under the partial unique index.
     */
    private void unsetCurrentDefaults(Long promoterId, Long keepId) {
        List<PromoterPayoutAccount> current = accountRepository
                .findByPromoterIdAndActiveTrueAndDefaultAccountTrue(promoterId).stream()
                .filter(a -> keepId == null || !keepId.equals(a.getId()))
                .toList();
        if (current.isEmpty()) return;
        current.forEach(a -> a.setDefaultAccount(false));
        accountRepository.saveAllAndFlush(current);
    }

    /** Full replace — a {@code null}/blank value clears the field. */
    private void apply(PromoterPayoutAccount account, PromoterPayoutAccountRequest req) {
        account.setPaymentMethod(resolvePaymentMethod(req.paymentMethodUuid()));
        account.setLabel(blankToNull(req.label()));
        account.setBank(resolveBank(req.bankUuid()));
        account.setAccountType(blankToNull(req.accountType()));
        account.setAccountNumber(blankToNull(req.accountNumber()));
        account.setAccountIdentifier(blankToNull(req.accountIdentifier()));
        account.setHolderName(blankToNull(req.holderName()));
        account.setHolderDocumentType(blankToNull(req.holderDocumentType()));
        account.setHolderDocumentNumber(blankToNull(req.holderDocumentNumber()));
        account.setPhone(blankToNull(req.phone()));
        account.setEmail(blankToNull(req.email()));
    }

    /**
     * Required fields per the method's {@code is_mandatory_*} flags (same
     * flags and meaning the payment-line forms use, V115/V130/V134/V136).
     * Package-private for unit tests.
     */
    static void validate(PromoterPayoutAccount a) {
        PaymentMethod method = a.getPaymentMethod();

        if ((a.getHolderDocumentType() == null) != (a.getHolderDocumentNumber() == null)) {
            throw new IllegalArgumentException("promoter_payout_account.holder_document.incomplete");
        }
        if (method.isMandatoryIdentification() && a.getHolderDocumentType() == null) {
            throw new IllegalArgumentException("promoter_payout_account.holder_document.required");
        }
        if (method.isMandatoryBank() && a.getBank() == null) {
            throw new IllegalArgumentException("promoter_payout_account.bank.required");
        }
        if (method.isMandatoryAccountType() && a.getAccountType() == null) {
            throw new IllegalArgumentException("promoter_payout_account.account_type.required");
        }
        if (method.isMandatoryAccountCode()) {
            // Structured national account (transfer/deposit/check): 20-digit VE number.
            if (a.getAccountNumber() == null) {
                throw new IllegalArgumentException("promoter_payout_account.account_number.required");
            }
            if (!a.getAccountNumber().matches(VE_ACCOUNT_NUMBER)) {
                throw new IllegalArgumentException("promoter_payout_account.account_number.format");
            }
        }
        if (method.isMandatoryBankAccount() && a.getAccountIdentifier() == null) {
            throw new IllegalArgumentException("promoter_payout_account.account_identifier.required");
        }
        if (method.isMandatoryPhone() && a.getPhone() == null) {
            throw new IllegalArgumentException("promoter_payout_account.phone.required");
        }
        if (method.isMandatoryEmail() && a.getEmail() == null) {
            throw new IllegalArgumentException("promoter_payout_account.email.required");
        }
    }

    private Promoter findPromoter(UUID uuid) {
        return promoterRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
    }

    /** Soft-deleted accounts are treated as gone (404), same as an unknown uuid. */
    private PromoterPayoutAccount findUnderPromoter(UUID promoterUuid, UUID accountUuid) {
        PromoterPayoutAccount account = accountRepository.findByUuid(accountUuid)
                .filter(PromoterPayoutAccount::isActive)
                .orElseThrow(() -> new NoSuchElementException("promoter_payout_account.not_found"));
        if (!account.getPromoter().getUuid().equals(promoterUuid)) {
            throw new NoSuchElementException("promoter_payout_account.not_found");
        }
        return account;
    }

    private PaymentMethod resolvePaymentMethod(UUID uuid) {
        PaymentMethod method = paymentMethodRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("payment_method.not_found"));
        if (!method.isActive()) {
            throw new IllegalArgumentException("promoter_payout_account.payment_method.inactive");
        }
        return method;
    }

    private Bank resolveBank(UUID uuid) {
        if (uuid == null) return null;
        return bankRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("bank.not_found"));
    }

    private static void ensureNotSystemRow(Promoter promoter) {
        if (promoter.isSystem()) {
            throw new IllegalArgumentException("promoter.system.not_editable");
        }
    }

    private static String blankToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    static PromoterPayoutAccountDto toDto(PromoterPayoutAccount a) {
        return new PromoterPayoutAccountDto(
                a.getUuid(),
                a.getPromoter().getUuid(),
                DisplayRefs.ref((Object) a.getPaymentMethod()),
                a.getPaymentMethod().getCode(),
                a.getLabel(),
                DisplayRefs.ref((Object) a.getBank()),
                a.getAccountType(),
                a.getAccountNumber(),
                a.getAccountIdentifier(),
                a.getHolderName(),
                a.getHolderDocumentType(),
                a.getHolderDocumentNumber(),
                a.getPhone(),
                a.getEmail(),
                a.isDefaultAccount(),
                a.isActive(),
                a.getStatus(),
                a.getCreatedAt(),
                a.getUpdatedAt());
    }
}
