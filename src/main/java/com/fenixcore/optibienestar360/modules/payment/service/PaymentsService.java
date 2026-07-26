package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.corporate.service.CorporateBillingResolver;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentApproveRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDiscountRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentRejectRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentSupportUrlDto;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment.PaymentStatus;
import com.fenixcore.optibienestar360.modules.payment.mapper.PaymentMapper;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionService;
import com.fenixcore.optibienestar360.modules.validator.service.ValidatorCacheService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
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

    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "status", "paymentMethod", "currency",
            "amount", "inscription",
            "paymentDate", "receivedAt", "appliedPeriod", "reviewedAt",
            "createdAt", "updatedAt", "active"
    );

    private static final String[] SEARCHABLE_FIELDS = {
            "referenceNumber", "adminNotes", "supportFileName"
    };

    /** Bounds for the presigned-URL TTL — clamps client-supplied values. */
    private static final Duration MIN_PRESIGNED_TTL = Duration.ofMinutes(1);
    private static final Duration MAX_PRESIGNED_TTL = Duration.ofHours(1);
    private static final Duration DEFAULT_PRESIGNED_TTL = Duration.ofMinutes(5);

    private final PaymentRepository paymentRepository;
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final PaymentMapper mapper;
    private final ObjectProvider<StorageService> storageProvider;
    private final EmailService emailService;
    private final MessageSource messageSource;
    private final ValidatorCacheService validatorCacheService;
    private final CommissionService commissionService;
    private final CorporateBillingResolver corporateBillingResolver;

    // ─── Read ───────────────────────────────────────────────────────────────

    public PaymentDto get(UUID uuid) {
        return mapper.toDto(findManaged(uuid));
    }

    /**
     * Powers {@code GET /v1/me/payments} — only the payments tied to the
     * caller's membership history are returned. Same sort default as the
     * admin list ({@code receivedAt DESC}) so the affiliate sees newest
     * first; no RSQL filter on this surface — the affiliate's view is
     * the full history, not a curated query.
     */
    public Page<PaymentDto> listForUser(UUID userUuid, Pageable pageable) {
        return paymentRepository.findOwnByUserUuid(userUuid, pageable).map(mapper::toDto);
    }

    public Page<PaymentDto> list(Pageable pageable, String filter, String q) {
        Specification<Payment> spec = activeOnly();
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "payment.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return paymentRepository.findAll(spec, pageable).map(mapper::toDto);
    }

    private static Specification<Payment> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
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

        // Corporate billing (V38): if the member belongs to an INSTITUTION_BULK
        // contract, bill the payment to the contract (and default the payer to
        // the contract's contact user when none was supplied). No-op for
        // ordinary affiliates and INDIVIDUAL_PAYER corporate members.
        corporateBillingResolver.applyBilling(payment);

        attachSupportFile(payment, supportFile);

        Payment saved = paymentRepository.save(payment);
        dispatchNotification(saved, "payment-received", "email.payment.received.subject");
        return mapper.toDto(saved);
    }

    // ─── Support file (presigned download URL) ─────────────────────────────

    /**
     * Generates a short-lived presigned download URL for the
     * proof-of-payment object in R2.
     *
     * <p>Failure modes (callers map these to HTTP):</p>
     * <ul>
     *   <li>Payment row missing → {@code payment.not_found} (404).</li>
     *   <li>{@code support_file_url} is {@code null} (no proof was
     *       attached, or the row was registered while R2 was off) →
     *       {@code payment.support.not_available} (404).</li>
     *   <li>{@link StorageService} bean not present (R2 disabled on this
     *       replica) → {@code payment.support.storage_unavailable}
     *       (422).</li>
     * </ul>
     *
     * @param paymentUuid the payment row
     * @param requestedTtl optional client-requested TTL. Clamped to
     *                     [{@value #MIN_PRESIGNED_TTL_MIN},
     *                     {@value #MAX_PRESIGNED_TTL_MIN}] minutes;
     *                     {@code null} defaults to 5 minutes.
     */
    public PaymentSupportUrlDto generateSupportUrl(UUID paymentUuid, Duration requestedTtl) {
        Payment payment = findManaged(paymentUuid);
        String key = payment.getSupportFileUrl();
        if (key == null || key.isBlank()) {
            throw new NoSuchElementException("payment.support.not_available");
        }

        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            throw new IllegalArgumentException("payment.support.storage_unavailable");
        }

        Duration ttl = clampTtl(requestedTtl);
        String url = storage.generatePresignedUrl(key, ttl);
        Instant expiresAt = Instant.now().plus(ttl);
        return new PaymentSupportUrlDto(
                url,
                expiresAt,
                ttl.toSeconds(),
                payment.getSupportFileName(),
                payment.getSupportFileContentType(),
                payment.getSupportFileSizeBytes());
    }

    private static Duration clampTtl(Duration requested) {
        if (requested == null) return DEFAULT_PRESIGNED_TTL;
        if (requested.compareTo(MIN_PRESIGNED_TTL) < 0) return MIN_PRESIGNED_TTL;
        if (requested.compareTo(MAX_PRESIGNED_TTL) > 0) return MAX_PRESIGNED_TTL;
        return requested;
    }

    // ─── Review workflow ───────────────────────────────────────────────────

    /**
     * Admin approves a pending payment. Status moves PENDING → APPROVED;
     * reviewer + timestamp are stamped (V23 CHECK
     * {@code chk_payments_review_consistency} requires them together with
     * any non-PENDING status). Optional reason becomes the approval note.
     *
     * @return updated DTO with the new review state
     */
    @Transactional
    public PaymentDto approve(UUID paymentUuid, PaymentApproveRequest request, UUID actorUserUuid) {
        Payment payment = findManaged(paymentUuid);
        ensurePending(payment);

        applyReview(payment, PaymentStatus.APPROVED, actorUserUuid,
                request != null ? request.reason() : null);

        validatorCacheService.evictForMembership(payment.getMembership());
        attributeCommission(payment);
        dispatchNotification(payment, "payment-approved", "email.payment.approved.subject");
        return mapper.toDto(payment);
    }

    /**
     * Fires the commission engine for the approved payment. Best-effort —
     * a failure to attribute (no promoter resolvable, calc error) is
     * logged and swallowed so the payment review stays committed. Admin
     * tooling can re-attribute via a future endpoint if necessary.
     */
    private void attributeCommission(Payment payment) {
        try {
            commissionService.calculateAndPersistFor(payment);
        } catch (RuntimeException ex) {
            log.error("Failed to attribute commission for payment {}", payment.getUuid(), ex);
        }
    }

    /**
     * Admin rejects a pending payment. Status moves PENDING → REJECTED;
     * reviewer + timestamp + reason all required (V23 CHECK
     * {@code chk_payments_rejection_has_reason} on top of the consistency
     * check). The reason is also surfaced to the affiliate so they know
     * what to fix and re-submit.
     */
    @Transactional
    public PaymentDto reject(UUID paymentUuid, PaymentRejectRequest request, UUID actorUserUuid) {
        Payment payment = findManaged(paymentUuid);
        ensurePending(payment);

        applyReview(payment, PaymentStatus.REJECTED, actorUserUuid, request.reason());

        validatorCacheService.evictForMembership(payment.getMembership());
        dispatchNotification(payment, "payment-rejected", "email.payment.rejected.subject");
        return mapper.toDto(payment);
    }

    /**
     * Applies a one-off discount to a single PENDING payment (v2 PDF item #1,
     * permission {@code ALLOWS_DISCOUNT}) — condone or reduce this specific
     * charge, distinct from a recurring subsidy. Only PENDING rows are eligible
     * (a reviewed payment is settled); the amount cannot exceed the payment
     * total. Audited inline via the {@code discount_*} columns.
     */
    @Transactional
    public PaymentDto applyDiscount(UUID paymentUuid, PaymentDiscountRequest request, UUID actorUserUuid) {
        Payment payment = findManaged(paymentUuid);
        if (!PaymentStatus.PENDING.name().equals(payment.getStatus())) {
            throw new IllegalArgumentException("payment.discount.not_pending");
        }
        if (request.amount().compareTo(payment.getAmount()) > 0) {
            throw new IllegalArgumentException("payment.discount.exceeds_amount");
        }
        User actor = userRepository.findByUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));

        payment.setDiscountAmount(request.amount());
        payment.setDiscountReason(request.reason());
        payment.setDiscountedBy(actor);
        payment.setDiscountedAt(Instant.now());
        return mapper.toDto(payment);   // managed → dirty-check on commit
    }

    private static void ensurePending(Payment payment) {
        if (!PaymentStatus.PENDING.name().equals(payment.getStatus())) {
            throw new IllegalArgumentException("payment.review.not_pending");
        }
    }

    private void applyReview(Payment payment, PaymentStatus targetStatus,
                             UUID actorUserUuid, String reason) {
        User reviewer = userRepository.findByUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        payment.setStatus(targetStatus.name());
        payment.setReviewedBy(reviewer);
        payment.setReviewedAt(Instant.now());
        payment.setReviewReason(reason);
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

    /**
     * Fires the templated notification email matching the payment event.
     * Failures are logged + swallowed — the payment row mutation already
     * landed (commit happens whether or not the SMTP call succeeds), and
     * the affiliate can find the result in the admin queue / their own
     * history. We never want a transient mail outage to roll back a
     * registration or a review.
     */
    private void dispatchNotification(Payment payment, String template, String subjectKey) {
        Person person = personFor(payment);
        if (person == null) {
            log.debug("Payment {} has no person attached — skipping notification", payment.getUuid());
            return;
        }
        String to = person.getEmail();
        if (to == null || to.isBlank()) {
            log.debug("Payment {}: member {} has no email — skipping notification",
                    payment.getUuid(), person.getUuid());
            return;
        }

        Locale locale = resolveLocale(person);
        String subject = messageSource.getMessage(subjectKey, null, locale);

        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", Optional.ofNullable(person.getFullName()).orElse(""));
        vars.put("planName", payment.getMembership().getPlan().getName());
        vars.put("amount", payment.getAmount());
        vars.put("currency", payment.getCurrency());
        vars.put("paymentMethod", payment.getPaymentMethod().name());
        vars.put("referenceNumber", payment.getReferenceNumber());
        vars.put("paymentDate", payment.getPaymentDate());
        vars.put("inscription", payment.isInscription());
        vars.put("appliedPeriod", payment.getAppliedPeriod());
        vars.put("reviewReason", payment.getReviewReason());

        try {
            emailService.sendTemplated(to, subject, template, locale, vars);
        } catch (RuntimeException ex) {
            log.error("Failed to dispatch {} email for payment {}", template, payment.getUuid(), ex);
        }
    }

    private static Person personFor(Payment payment) {
        Membership membership = payment.getMembership();
        if (membership == null) return null;
        Member member = membership.getMember();
        return member != null ? member.getPerson() : null;
    }

    private static Locale resolveLocale(Person person) {
        String tag = person.getLocale();
        if (tag == null || tag.isBlank()) return Locale.forLanguageTag("es");
        try {
            return Locale.forLanguageTag(tag);
        } catch (RuntimeException ex) {
            return Locale.forLanguageTag("es");
        }
    }
}
