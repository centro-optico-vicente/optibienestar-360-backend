package com.fenixcore.optisaludplus.modules.payment.service;

import com.fenixcore.optisaludplus.common.service.StorageService;
import com.fenixcore.optisaludplus.modules.auth.entity.User;
import com.fenixcore.optisaludplus.modules.auth.repository.UserRepository;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership;
import com.fenixcore.optisaludplus.modules.membership.repository.MembershipRepository;
import com.fenixcore.optisaludplus.modules.payment.dto.PaymentCreateRequest;
import com.fenixcore.optisaludplus.modules.payment.dto.PaymentDto;
import com.fenixcore.optisaludplus.modules.payment.entity.Payment;
import com.fenixcore.optisaludplus.modules.payment.entity.Payment.PaymentStatus;
import com.fenixcore.optisaludplus.modules.payment.mapper.PaymentMapper;
import com.fenixcore.optisaludplus.modules.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Application service for {@link Payment} — admin registration of a manual
 * payment record. Subsequent bullets (approve / reject / list / support
 * download) extend this class.
 *
 * <p>Plural-name convention ({@code PaymentsService}) matches
 * {@code PlansService}, {@code MembershipsService}, {@code AlliesService}.</p>
 *
 * <p><b>Proof of payment storage:</b> the file part of the multipart
 * request is optional. The service captures the metadata (name,
 * content_type, size) into the payment row unconditionally, but the
 * actual byte upload is delegated to {@link StorageService} which is
 * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
 * @ConditionalOnProperty}({@code storage.r2.enabled=true}). Injected via
 * {@link ObjectProvider} so the service still works in environments where
 * R2 is off (today: file content is discarded, {@code support_file_url}
 * stays {@code null}; tomorrow: enable the flag and the same code path
 * starts uploading without changes).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PaymentsService {

    /** R2 key prefix for payment proofs. */
    private static final String STORAGE_PREFIX = "payments/proofs/";

    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PaymentMapper mapper;
    private final ObjectProvider<StorageService> storageProvider;

    // ─── Read ───────────────────────────────────────────────────────────────

    public PaymentDto get(UUID uuid) {
        return mapper.toDto(findManaged(uuid));
    }

    // ─── Create ─────────────────────────────────────────────────────────────

    /**
     * Registers a new payment. Status starts at {@code PENDING}; admin
     * review happens through the approve/reject endpoints (separate
     * bullets).
     *
     * @param request   JSON metadata from the multipart payload
     * @param supportFile  optional proof-of-payment file (may be null /
     *                  empty when the admin only registers the metadata)
     */
    @Transactional
    public PaymentDto register(PaymentCreateRequest request, MultipartFile supportFile) {
        Membership membership = membershipRepository.findByUuid(request.membershipUuid())
                .orElseThrow(() -> new NoSuchElementException("membership.not_found"));

        Payment payment = new Payment();
        payment.setMembership(membership);

        if (request.payerUserUuid() != null) {
            User payer = userRepository.findByUuid(request.payerUserUuid())
                    .orElseThrow(() -> new NoSuchElementException("user.not_found"));
            payment.setPayerUser(payer);
        }

        payment.setAmount(request.amount());
        payment.setCurrency(request.currency() != null ? request.currency() : "USD");
        payment.setPaymentMethod(request.paymentMethod());
        payment.setReferenceNumber(request.referenceNumber());
        payment.setPaymentDate(request.paymentDate());

        boolean inscription = Boolean.TRUE.equals(request.inscription());
        payment.setInscription(inscription);
        payment.setAppliedPeriod(resolveAppliedPeriod(inscription, request.appliedPeriod()));

        payment.setAdminNotes(request.adminNotes());
        payment.setStatus(PaymentStatus.PENDING.name());

        attachSupportFile(payment, supportFile);

        Payment saved = paymentRepository.save(payment);
        return mapper.toDto(saved);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private Payment findManaged(UUID uuid) {
        return paymentRepository.findByUuid(uuid)
                .orElseThrow(() -> new NoSuchElementException("payment.not_found"));
    }

    /**
     * Coherent default: for recurring (non-inscription) rows that come
     * without an applied_period, set it to the first day of the current
     * month. Mirrors the V23 inscription_no_period CHECK constraint
     * intent without requiring the admin to retype the value every time.
     */
    private static LocalDate resolveAppliedPeriod(boolean inscription, LocalDate explicit) {
        if (inscription) {
            return null;
        }
        if (explicit != null) {
            return explicit.withDayOfMonth(1);
        }
        return LocalDate.now().withDayOfMonth(1);
    }

    /**
     * Captures file metadata into the payment row and, when R2 is enabled,
     * uploads the bytes to the bucket. When R2 is off, the file content is
     * discarded silently — the metadata still lands so the admin sees that
     * a proof was attached and can re-upload later.
     */
    private void attachSupportFile(Payment payment, MultipartFile file) {
        if (file == null || file.isEmpty()) return;

        payment.setSupportFileName(file.getOriginalFilename());
        payment.setSupportFileContentType(file.getContentType());
        payment.setSupportFileSizeBytes(file.getSize());

        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            log.info("R2 storage disabled — payment {} support file metadata captured but bytes discarded",
                    payment.getUuid());
            return;
        }

        String key = STORAGE_PREFIX + UUID.randomUUID() + "-" + safeName(file.getOriginalFilename());
        try {
            storage.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
            payment.setSupportFileUrl(key);
        } catch (IOException ex) {
            log.error("Failed to read support file stream for payment registration", ex);
            throw new IllegalArgumentException("payment.support.upload_failed");
        }
    }

    private static String safeName(String original) {
        if (original == null || original.isBlank()) return "proof";
        return original.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
