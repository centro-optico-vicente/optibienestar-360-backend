package com.fenixcore.optibienestar360.modules.payment.service;

import com.fenixcore.optibienestar360.core.audit.AuditAction;
import com.fenixcore.optibienestar360.core.audit.Auditable;
import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.common.service.StorageService;
import com.fenixcore.optibienestar360.common.storage.FileValidationService;
import com.fenixcore.optibienestar360.common.storage.FileVisibility;
import com.fenixcore.optibienestar360.common.storage.PresignedUrlPolicy;
import com.fenixcore.optibienestar360.common.storage.StorageKeyBuilder;
import com.fenixcore.optibienestar360.core.util.DefaultSortResolver;
import com.fenixcore.optibienestar360.core.util.RsqlFieldValidator;
import com.fenixcore.optibienestar360.core.util.SearchSpecifications;
import com.fenixcore.optibienestar360.core.util.SortFieldValidator;
import com.fenixcore.optibienestar360.core.util.SortOrder;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import com.fenixcore.optibienestar360.modules.bank.repository.BankRepository;
import com.fenixcore.optibienestar360.modules.corporate.service.CorporateBillingResolver;
import com.fenixcore.optibienestar360.modules.currency.repository.CurrencyRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.member.repository.MemberRepository;
import com.fenixcore.optibienestar360.modules.membership.entity.Membership;
import com.fenixcore.optibienestar360.modules.membership.repository.MembershipRepository;
import com.fenixcore.optibienestar360.modules.membership.service.MembershipChargeService;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationChannelResolver;
import com.fenixcore.optibienestar360.modules.notification.service.NotificationChannelResolver.RecipientType;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import com.fenixcore.optibienestar360.modules.person.repository.PersonRepository;
import com.fenixcore.optibienestar360.modules.payment.dto.DownlinePaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.MyPaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentApproveRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDiscountRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentDto;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentRejectRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.PaymentSupportUrlDto;
import com.fenixcore.optibienestar360.modules.payment.dto.OutPaymentCreateRequest;
import com.fenixcore.optibienestar360.modules.payment.dto.OutPaymentUpdateRequest;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment;
import com.fenixcore.optibienestar360.modules.payment.entity.Payment.PaymentStatus;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentCategory;
import com.fenixcore.optibienestar360.modules.payment.entity.PaymentLine;
import com.fenixcore.optibienestar360.modules.payment.mapper.PaymentMapper;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentCategoryRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentMethodRepository;
import com.fenixcore.optibienestar360.modules.payment.repository.PaymentRepository;
import com.fenixcore.optibienestar360.modules.promoter.entity.Promoter;
import com.fenixcore.optibienestar360.modules.promoter.repository.PromoterRepository;
import com.fenixcore.optibienestar360.modules.promoter.service.CommissionService;
import com.fenixcore.optibienestar360.modules.validator.service.ValidatorCacheService;
import io.github.perplexhub.rsql.RSQLJPASupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
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
@RequiredArgsConstructor(onConstructor_ = @Autowired)
@Transactional(readOnly = true)
@Slf4j
public class PaymentsService {

    private static final String OWNER_TABLE = "payments";
    private static final FileVisibility VISIBILITY = FileVisibility.CONFIDENTIAL;

    /**
     * {@code paymentMethod} dropped since V117 — it now lives on
     * {@code payment_lines}, a collection, not a direct RSQL-filterable
     * property of {@code Payment} anymore. Re-add once a dedicated
     * cross-line filter is designed (hub plan "Movimientos" screen).
     */
    /** {@code direction} is its own first-class {@code list} param, not routed through this RSQL filter — see {@link #list}'s Javadoc. */
    private static final Set<String> ALLOWED_FILTER_FIELDS = Set.of(
            "status", "currency.code",
            "amount", "inscription",
            "paymentDate", "receivedAt", "appliedPeriod", "reviewedAt",
            "createdAt", "updatedAt", "active"
    );

    /** {@code referenceNumber} dropped since V117 — now on {@code payment_lines}, see {@link #ALLOWED_FILTER_FIELDS}. */
    private static final String[] SEARCHABLE_FIELDS = {
            "adminNotes", "supportFileName"
    };

    /** {@code plan_Display} → plan's catalog name (ADR 0014 default; only FK Payment's list DTO surfaces as a display column). */
    private static final Map<String, SortFieldValidator.SortableField> SORTABLE_FIELDS =
            SortFieldValidator.sortableFieldsOf(Payment.class, Map.of(
                "plan_Display", "plan.name",
                // The admin table's "plan reference" column actually renders `plan_Code`, not `plan_Display` — alias both.
                "plan_Code", "plan.code"
            ));

    private final PaymentRepository paymentRepository;
    private final PaymentCategoryRepository paymentCategoryRepository;
    private final PaymentMethodRepository paymentMethodRepository;
    private final PromoterRepository promoterRepository;
	private final PersonRepository personRepository;
	private final BankRepository bankRepository;
    private final MembershipRepository membershipRepository;
    private final MemberRepository memberRepository;
    private final UserRepository userRepository;
    private final CurrencyRepository currencyRepository;
    private final com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService currencyConversionService;
    private final PaymentMapper mapper;
    private final DefaultSortResolver defaultSortResolver;
    private final ObjectProvider<StorageService> storageProvider;
    private final EmailService emailService;
    private final MessageSource messageSource;
    private final ValidatorCacheService validatorCacheService;
    private final CommissionService commissionService;
    private final com.fenixcore.optibienestar360.modules.promoter.service.HierarchyOverrideService hierarchyOverrideService;
    private final CorporateBillingResolver corporateBillingResolver;
    private final PresignedUrlPolicy presignedUrlPolicy;
    private final FileValidationService fileValidationService;
    private final MembershipChargeService membershipChargeService;
    private final NotificationChannelResolver notificationChannelResolver;

    /** {@code administración} recipient for the new-payment and review-decision promoter/admin notices (V153). */
    @Value("${mail.admin}")
    private String adminEmail;

    /** Compatibility constructor retained for existing unit tests and integrations. */
    public PaymentsService(
		PaymentRepository paymentRepository,
		PaymentCategoryRepository paymentCategoryRepository,
		PaymentMethodRepository paymentMethodRepository,
		PromoterRepository promoterRepository,
		MembershipRepository membershipRepository,
		MemberRepository memberRepository,
		UserRepository userRepository,
		CurrencyRepository currencyRepository,
		com.fenixcore.optibienestar360.modules.currency.service.CurrencyConversionService currencyConversionService,
		PaymentMapper mapper,
		DefaultSortResolver defaultSortResolver,
		ObjectProvider<StorageService> storageProvider,
		EmailService emailService,
		MessageSource messageSource,
		ValidatorCacheService validatorCacheService,
		CommissionService commissionService,
		com.fenixcore.optibienestar360.modules.promoter.service.HierarchyOverrideService hierarchyOverrideService,
		CorporateBillingResolver corporateBillingResolver,
		PresignedUrlPolicy presignedUrlPolicy,
		FileValidationService fileValidationService,
		MembershipChargeService membershipChargeService,
		NotificationChannelResolver notificationChannelResolver
	) {
        this(paymentRepository, paymentCategoryRepository, paymentMethodRepository, promoterRepository,
                null, null, membershipRepository, memberRepository, userRepository, currencyRepository,
                currencyConversionService, mapper, defaultSortResolver, storageProvider, emailService,
                messageSource, validatorCacheService, commissionService, hierarchyOverrideService,
                corporateBillingResolver, presignedUrlPolicy, fileValidationService,
                membershipChargeService, notificationChannelResolver);
    }

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

    /**
     * Powers {@code GET /v1/promoter/me/payments} — the two promoter
     * self-service surfaces (hub plan payments-unification, "Mis portales"):
     * {@code direction=IN} = "Cobros de mis afiliados" (collections from the
     * affiliates in the promoter's downline, filtered by the denormalized
     * {@code payments.promoter_id}, V117); {@code direction=OUT} = "Mis pagos
     * de comisiones" (commission payouts disbursed to the promoter,
     * CommissionPayoutService). No RSQL filter on this surface, same
     * reasoning as {@link #listForUser} — the promoter's view is a curated
     * subset (their own network's rows), not a query builder.
     */
    public Page<PaymentDto> listForPromoter(UUID actorUserUuid, String direction, Pageable pageable) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted("payment", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "payment");
        Specification<Payment> spec = activeOnly()
                .and(directionIs(direction))
                .and((root, query, cb) -> cb.equal(root.get("promoter").get("id"), promoter.getId()));
        return paymentRepository.findAll(spec, resolvedPageable).map(mapper::toDto);
    }

    /**
     * @param direction {@code "IN"}/{@code "OUT"} to constrain to one
     *                  direction, {@code "ALL"} for both (the "Movimientos"
     *                  screen), or {@code null}/blank to default to {@code
     *                  IN} — the historical behavior of this screen ("Cobros"
     *                  in Afiliaciones/Finanzas) from before payments also
     *                  held commission payouts (V117). A first-class param
     *                  rather than folding it into the free-text RSQL {@code
     *                  filter}: {@code RsqlFieldValidator}'s field-extractor
     *                  regex only understands {@code ==}/{@code !=}/{@code
     *                  <}/{@code >}, not the {@code =in=} FIQL operator a
     *                  multi-value direction filter would need — trying to
     *                  route "both directions" through the generic filter
     *                  string 422's ({@code Campo de filtro no permitido}, it
     *                  misparses the operator's own {@code in} token as an
     *                  unknown field).
     */
    public Page<PaymentDto> list(Pageable pageable, String filter, String q, String direction) {
        Pageable defaultedPageable = defaultSortResolver.withDefaultSortIfUnsorted(
                "payment", pageable);
        Pageable resolvedPageable = SortFieldValidator.resolve(defaultedPageable, SORTABLE_FIELDS, "payment");
        Specification<Payment> spec = activeOnly();
        if (direction == null || direction.isBlank()) {
            spec = spec.and(directionIs("IN"));
        }
        else if (!"ALL".equals(direction)) {
            spec = spec.and(directionIs(direction));
        }
        if (filter != null && !filter.isBlank()) {
            RsqlFieldValidator.validate(filter, ALLOWED_FILTER_FIELDS,
                    "payment.filter.field_not_allowed");
            spec = spec.and(RSQLJPASupport.toSpecification(filter));
        }
        if (q != null && !q.isBlank()) {
            spec = spec.and(SearchSpecifications.acrossFields(q, SEARCHABLE_FIELDS));
        }
        return paymentRepository.findAll(spec, resolvedPageable).map(mapper::toDto);
    }

    private static Specification<Payment> directionIs(String direction) {
        return (root, query, cb) -> cb.equal(root.get("direction"), direction);
    }

    /** The sort {@link #list} actually applies — see {@link DefaultSortResolver#effectiveSort}. */
    public List<SortOrder> effectiveSort(Pageable pageable) {
        return defaultSortResolver.effectiveSort("payment", pageable);
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
    @Auditable(entity = "payment", action = AuditAction.CREATE)
    public PaymentDto register(PaymentCreateRequest request, MultipartFile supportFile) {
        Membership membership = membershipRepository.findByUuid(request.membershipUuid())
                .orElseThrow(() -> new NoSuchElementException("membership.not_found"));
        User payer = request.payerUserUuid() != null
                ? userRepository.findByUuid(request.payerUserUuid())
                        .orElseThrow(() -> new NoSuchElementException("user.not_found"))
                : null;
        return registerInternal(membership, payer, request.amount(), request.currency(),
                request.paymentMethodUuid(), request.bankUuid(), request.identification(),
                request.bankAccountType(), request.bankAccountCode(), request.bankAccountIdentifier(),
                request.phone(), request.email(), request.referenceNumber(), request.paymentDate(),
                request.inscription(), request.appliedPeriod(), request.adminNotes(), supportFile);
    }

    @Transactional
    @Auditable(entity = "payment", action = AuditAction.CREATE)
    public PaymentDto registerOut(OutPaymentCreateRequest request, MultipartFile supportFile) {
        Payment payment = new Payment();
        applyOutFields(payment, request.paymentCategoryUuid(), request.promoterUuid(), request.personUuid(),
                request.amount(), request.currency(), request.paymentMethodUuid(), request.bankUuid(),
                request.identification(), request.bankAccountType(), request.bankAccountCode(),
                request.bankAccountIdentifier(), request.phone(), request.email(), request.referenceNumber(),
                request.paymentDate(), request.adminNotes());
        payment.setDirection("OUT");
        payment.setStatus(PaymentStatus.DRAFT.name());
        attachSupportFile(payment, supportFile);
        return mapper.toDto(paymentRepository.save(payment));
    }

    @Transactional
    @Auditable(entity = "payment", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PaymentDto updateOut(UUID uuid, OutPaymentUpdateRequest request) {
        Payment payment = findManaged(uuid);
        ensureOutDraft(payment);
        applyOutFields(payment, request.paymentCategoryUuid(), request.promoterUuid(), request.personUuid(),
                request.amount(), request.currency(), request.paymentMethodUuid(), request.bankUuid(),
                request.identification(), request.bankAccountType(), request.bankAccountCode(),
                request.bankAccountIdentifier(), request.phone(), request.email(), request.referenceNumber(),
                request.paymentDate(), request.adminNotes());
        return mapper.toDto(payment);
    }

    @Transactional
    public PaymentDto processOut(UUID uuid) {
        Payment payment = findManaged(uuid);
        ensureOutDraft(payment);
        payment.setStatus(PaymentStatus.PENDING.name());
        for (PaymentLine line : payment.getLines()) line.setStatus(PaymentStatus.PENDING.name());
        return mapper.toDto(payment);
    }

    @Transactional
    public PaymentDto approveOut(UUID uuid, PaymentApproveRequest request, UUID actorUserUuid) {
        Payment payment = findManaged(uuid);
        ensureOut(payment);
        return approve(uuid, request, actorUserUuid);
    }

    @Transactional
    public PaymentDto rejectOut(UUID uuid, PaymentRejectRequest request, UUID actorUserUuid) {
        Payment payment = findManaged(uuid);
        ensureOut(payment);
        return reject(uuid, request, actorUserUuid);
    }

    @Transactional
    @Auditable(entity = "payment", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void removeOut(UUID uuid) {
        Payment payment = findManaged(uuid);
        ensureOutDraft(payment);
        payment.setActive(false);
    }

    private void applyOutFields(Payment payment, UUID categoryUuid, UUID promoterUuid, UUID personUuid,
                                BigDecimal amount, String currencyCode, UUID methodUuid, UUID bankUuid,
                                String identification, String bankAccountType, String bankAccountCode,
                                String bankAccountIdentifier, String phone, String email, String referenceNumber,
                                Instant paymentDate, String adminNotes) {
        PaymentCategory category = paymentCategoryRepository.findByUuid(categoryUuid)
                .filter(PaymentCategory::isActive).filter(c -> "OUT".equals(c.getDirection()))
                .orElseThrow(() -> new NoSuchElementException("payment_category.out_not_found"));
        Promoter promoter = promoterRepository.findByUuid(promoterUuid)
                .filter(Promoter::isActive).orElseThrow(() -> new NoSuchElementException("promoter.not_found"));
        Person person = personRepository.findByUuid(personUuid)
                .filter(Person::isActive).orElseThrow(() -> new NoSuchElementException("person.not_found"));
        if (promoter.getPerson() == null || !promoter.getPerson().getId().equals(person.getId())) {
            throw new IllegalArgumentException("payment.out.person_promoter_mismatch");
        }
        var currency = currencyRepository.findByCode(currencyCode == null ? "USD" : currencyCode)
                .filter(com.fenixcore.optibienestar360.modules.currency.entity.Currency::isActive)
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
        var method = paymentMethodRepository.findByUuid(methodUuid)
                .filter(com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod::isActive)
                .orElseThrow(() -> new NoSuchElementException("payment_method.not_found"));
        if (method.isMandatoryReferenceNumber() && (referenceNumber == null || referenceNumber.isBlank()))
            throw new IllegalArgumentException("payment.reference.required");
        if (method.isMandatoryPhone() && (phone == null || phone.isBlank()))
            throw new IllegalArgumentException("payment.phone.required");
        if (method.isMandatoryEmail() && (email == null || email.isBlank()))
            throw new IllegalArgumentException("payment.email.required");
        Bank bank = bankUuid == null ? null : bankRepository.findByUuid(bankUuid)
                .filter(Bank::isActive).orElseThrow(() -> new NoSuchElementException("bank.not_found"));
        if (method.isMandatoryBank() && bank == null)
            throw new IllegalArgumentException("payment.bank.required");
        if (method.isMandatoryBankAccount() &&
                (bankAccountIdentifier == null || bankAccountIdentifier.isBlank()))
            throw new IllegalArgumentException("payment.bank_account.required");

        payment.setPaymentType(category);
        payment.setPromoter(promoter);
        payment.setPerson(person);
        payment.setMembership(null);
        payment.setPayerUser(null);
        payment.setAmount(amount);
        payment.setCurrency(currency);
        payment.setPaymentDate(paymentDate);
        payment.setAdminNotes(adminNotes);
        PaymentLine line = payment.getLines().stream().findFirst().orElseGet(() -> {
            PaymentLine created = new PaymentLine();
            created.setPayment(payment);
            payment.getLines().add(created);
            return created;
        });
        line.setPaymentType(method);
        line.setBank(bank);
        line.setAmount(amount);
        line.setCurrency(currency);
        line.setIdentification(identification);
        line.setBankAccountType(bankAccountType);
        line.setBankAccountCode(bankAccountCode);
        line.setBankAccountIdentifier(bankAccountIdentifier);
        line.setPhone(phone);
        line.setEmail(email);
        line.setReferenceNumber(referenceNumber);
        line.setStatus(payment.getStatus() == null ? PaymentStatus.DRAFT.name() : payment.getStatus());
    }

    private static void ensureOutDraft(Payment payment) {
        if (!"OUT".equals(payment.getDirection())) throw new IllegalArgumentException("payment.out.required");
        if (!PaymentStatus.DRAFT.name().equals(payment.getStatus()))
            throw new IllegalArgumentException("payment.out.not_draft");
    }

    private static void ensureOut(Payment payment) {
        if (!"OUT".equals(payment.getDirection())) {
            throw new IllegalArgumentException("payment.out.required");
        }
    }

    /**
     * {@code POST /v1/me/payments} — an affiliate registering their own
     * payment. The membership is resolved from the caller (their current
     * active one, mirroring {@code member.membership already_active}'s
     * "one active membership" invariant), never from client input.
     */
    @Transactional
    @Auditable(entity = "payment", action = AuditAction.CREATE)
    public PaymentDto registerOwn(UUID actorUserUuid, MyPaymentCreateRequest request, MultipartFile supportFile) {
        Member member = memberRepository.findByUserUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        Membership membership = membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId())
                .orElseThrow(() -> new NoSuchElementException("membership.active.not_found"));
        return registerInternal(membership, null, request.amount(), request.currency(),
                request.paymentMethodUuid(), request.bankUuid(), request.identification(),
                request.bankAccountType(), request.bankAccountCode(), request.bankAccountIdentifier(),
                request.phone(), request.email(), request.referenceNumber(), request.paymentDate(),
                request.inscription(), request.appliedPeriod(), request.adminNotes(), supportFile);
    }

    /**
     * {@code POST /v1/promoter/me/payments} — a promoter registering a
     * collection on behalf of an affiliate in their own downline. Reuses
     * the {@code ownedMember}-style ownership check {@code
     * PromoterCollectionService} already applies for reminders/payment
     * promises: a member outside the caller's portfolio surfaces the same
     * 404 as a member that doesn't exist, so a promoter can't probe
     * affiliates outside their book.
     */
    @Transactional
    @Auditable(entity = "payment", action = AuditAction.CREATE)
    public PaymentDto registerForDownline(UUID actorUserUuid, DownlinePaymentCreateRequest request, MultipartFile supportFile) {
        Promoter promoter = promoterRepository.findActiveByUserUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("me.promoter.not_found"));
        Member member = memberRepository.findByUuid(request.memberUuid())
                .orElseThrow(() -> new NoSuchElementException("member.not_found"));
        if (member.getPromoter() == null || !member.getPromoter().getId().equals(promoter.getId())) {
            throw new NoSuchElementException("promoter.member.not_in_portfolio");
        }
        Membership membership = membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId())
                .orElseThrow(() -> new NoSuchElementException("membership.active.not_found"));
        return registerInternal(membership, null, request.amount(), request.currency(),
                request.paymentMethodUuid(), request.bankUuid(), request.identification(),
                request.bankAccountType(), request.bankAccountCode(), request.bankAccountIdentifier(),
                request.phone(), request.email(), request.referenceNumber(), request.paymentDate(),
                request.inscription(), request.appliedPeriod(), request.adminNotes(), supportFile);
    }

    /**
     * Shared build+save logic behind {@link #register}, {@link #registerOwn} and
     * {@link #registerForDownline}. Method/bank/mandatory-field handling mirrors
     * {@link #applyOutFields} — same {@code payment_methods} catalog, same
     * mandatory-flag validation (e.g. pago móvil requires a bank but not an
     * account number; cheque/transferencia require both).
     */
    private PaymentDto registerInternal(Membership membership, User payer, BigDecimal amount, String currencyCode,
                                        UUID methodUuid, UUID bankUuid, String identification,
                                        String bankAccountType, String bankAccountCode, String bankAccountIdentifier,
                                        String phone, String email, String referenceNumber, Instant paymentDate,
                                        Boolean inscriptionFlag, LocalDate appliedPeriod, String adminNotes,
                                        MultipartFile supportFile) {
        Payment payment = new Payment();
        payment.setMembership(membership);
        if (payer != null) {
            payment.setPayerUser(payer);
        }

        payment.setAmount(amount);
        var currency = currencyRepository.findByCode(currencyCode != null ? currencyCode : "USD")
                .orElseThrow(() -> new NoSuchElementException("currency.not_found"));
        payment.setCurrency(currency);
        payment.setPaymentDate(paymentDate);

        var method = paymentMethodRepository.findByUuid(methodUuid)
                .filter(com.fenixcore.optibienestar360.modules.payment.entity.PaymentMethod::isActive)
                .orElseThrow(() -> new NoSuchElementException("payment_method.not_found"));
        if (method.isMandatoryReferenceNumber() && (referenceNumber == null || referenceNumber.isBlank()))
            throw new IllegalArgumentException("payment.reference.required");
        if (method.isMandatoryPhone() && (phone == null || phone.isBlank()))
            throw new IllegalArgumentException("payment.phone.required");
        if (method.isMandatoryEmail() && (email == null || email.isBlank()))
            throw new IllegalArgumentException("payment.email.required");
        Bank bank = bankUuid == null ? null : bankRepository.findByUuid(bankUuid)
                .filter(Bank::isActive).orElseThrow(() -> new NoSuchElementException("bank.not_found"));
        if (method.isMandatoryBank() && bank == null)
            throw new IllegalArgumentException("payment.bank.required");
        if (method.isMandatoryBankAccount() &&
                (bankAccountIdentifier == null || bankAccountIdentifier.isBlank()))
            throw new IllegalArgumentException("payment.bank_account.required");

        boolean inscription = Boolean.TRUE.equals(inscriptionFlag);
        payment.setInscription(inscription);
        payment.setAppliedPeriod(resolveAppliedPeriod(inscription, appliedPeriod));

        payment.setAdminNotes(adminNotes);
        payment.setStatus(PaymentStatus.PENDING.name());

        // Header (V117): this flow only ever produces a collection (IN),
        // never a commission payout (OUT — CommissionPayoutService).
        payment.setDirection("IN");
        payment.setPaymentType(resolvePaymentCategory(inscription));
        payment.setPerson(membership.getMember().getPerson());
        payment.setPromoter(membership.getMember().getPromoter());

        // Corporate billing (V38): if the member belongs to an INSTITUTION_BULK
        // contract, bill the payment to the contract (and default the payer to
        // the contract's contact user when none was supplied). No-op for
        // ordinary affiliates and INDIVIDUAL_PAYER corporate members.
        corporateBillingResolver.applyBilling(payment);

        attachSupportFile(payment, supportFile);

        PaymentLine line = new PaymentLine();
        line.setPayment(payment);
        line.setPaymentType(method);
        line.setBank(bank);
        line.setAmount(payment.getAmount());
        line.setCurrency(currency);
        line.setIdentification(identification);
        line.setBankAccountType(bankAccountType);
        line.setBankAccountCode(bankAccountCode);
        line.setBankAccountIdentifier(bankAccountIdentifier);
        line.setPhone(phone);
        line.setEmail(email);
        line.setReferenceNumber(referenceNumber);
        line.setStatus(PaymentStatus.PENDING.name());
        payment.getLines().add(line);

        Payment saved = paymentRepository.save(payment);
        dispatchNotification(saved, "payment-received", "email.payment.received.subject");
        notifySubmissionToPromoterAndAdmin(saved);
        return mapper.toDto(saved);
    }

    // ─── Delete (PENDING only — a mistaken registration, not yet reviewed) ──

    /** Admin: any PENDING payment. {@code PAYMENT_DELETE}. */
    @Transactional
    @Auditable(entity = "payment", action = AuditAction.DELETE, uuidArgIndex = 0)
    public void remove(UUID uuid) {
        Payment payment = findManaged(uuid);
        ensureDeletable(payment);
        payment.setActive(false);
    }

    /** Affiliate: only their own PENDING payment. {@code PAYMENT_DELETE_OWN}. */
    @Transactional
    @Auditable(entity = "payment", action = AuditAction.DELETE, uuidArgIndex = 1)
    public void removeOwn(UUID actorUserUuid, UUID uuid) {
        Payment payment = findManaged(uuid);
        if (!ownedByUser(payment, actorUserUuid)) {
            throw new NoSuchElementException("payment.not_found");
        }
        ensureDeletable(payment);
        payment.setActive(false);
    }

    /** Promoter: only a PENDING collection from their own downline. {@code PAYMENT_DELETE_DOWNLINE}. */
    @Transactional
    @Auditable(entity = "payment", action = AuditAction.DELETE, uuidArgIndex = 1)
    public void removeForDownline(UUID actorUserUuid, UUID uuid) {
        Payment payment = findManaged(uuid);
        if (!ownedByPromoterDownline(payment, actorUserUuid)) {
            throw new NoSuchElementException("payment.not_found");
        }
        ensureDeletable(payment);
        payment.setActive(false);
    }

    private static void ensureDeletable(Payment payment) {
        if (!PaymentStatus.PENDING.name().equals(payment.getStatus())) {
            throw new IllegalArgumentException("payment.delete.not_pending");
        }
    }

    private boolean ownedByUser(Payment payment, UUID actorUserUuid) {
        Person person = personFor(payment);
        if (person == null) return false;
        return userRepository.findByUuid(actorUserUuid)
                .map(u -> u.getPerson() != null && u.getPerson().getId().equals(person.getId()))
                .orElse(false);
    }

    private boolean ownedByPromoterDownline(Payment payment, UUID actorUserUuid) {
        if (!"IN".equals(payment.getDirection()) || payment.getPromoter() == null) return false;
        return promoterRepository.findActiveByUserUuid(actorUserUuid)
                .map(promoter -> payment.getPromoter().getId().equals(promoter.getId()))
                .orElse(false);
    }

    // ─── Review workflow (promoter downline) ────────────────────────────────

    /** Promoter: approves a PENDING collection from their own downline. {@code PAYMENT_APPROVE_DOWNLINE}. */
    @Transactional
    @Auditable(entity = "payment", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public PaymentDto approveForDownline(UUID actorUserUuid, UUID uuid, PaymentApproveRequest request) {
        Payment payment = findManaged(uuid);
        if (!ownedByPromoterDownline(payment, actorUserUuid)) {
            throw new NoSuchElementException("payment.not_found");
        }
        return approve(uuid, request, actorUserUuid);
    }

    /** Promoter: rejects a PENDING collection from their own downline. {@code PAYMENT_REJECT_DOWNLINE}. */
    @Transactional
    @Auditable(entity = "payment", action = AuditAction.UPDATE, uuidArgIndex = 1)
    public PaymentDto rejectForDownline(UUID actorUserUuid, UUID uuid, PaymentRejectRequest request) {
        Payment payment = findManaged(uuid);
        if (!ownedByPromoterDownline(payment, actorUserUuid)) {
            throw new NoSuchElementException("payment.not_found");
        }
        return reject(uuid, request, actorUserUuid);
    }

    /** {@code inscription} → {@code INSCRIPTION_FEE}, else {@code MEMBERSHIP_FEE} (V115 seed, same split V117's backfill used). */
    private PaymentCategory resolvePaymentCategory(boolean inscription) {
        String code = inscription ? "INSCRIPTION_FEE" : "MEMBERSHIP_FEE";
        return paymentCategoryRepository.findByCode(code)
                .orElseThrow(() -> new NoSuchElementException("payment_category.not_found"));
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

        Duration ttl = presignedUrlPolicy.clamp(requestedTtl);
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
    @Auditable(entity = "payment", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PaymentDto approve(UUID paymentUuid, PaymentApproveRequest request, UUID actorUserUuid) {
        Payment payment = findManaged(paymentUuid);
        if ("OUT".equals(payment.getDirection())) {
            throw new IllegalArgumentException("payment.collection.required");
        }
        ensurePending(payment);

        applyReview(payment, PaymentStatus.APPROVED, actorUserUuid,
                request != null ? request.reason() : null);
        if (!"OUT".equals(payment.getDirection())) {
            snapshotExchangeRate(payment);
            validatorCacheService.evictForMembership(payment.getMembership());
            attributeCommission(payment);
            confirmMemberOnFirstApprovedPayment(payment);
            applyMembershipCharges(payment);
        }
        dispatchNotification(payment, "payment-approved", "email.payment.approved.subject");
        notifyPromoterOfDecision(payment, "APPROVED");
        return mapper.toDto(payment);
    }

    /**
     * Settles the {@code MembershipCharge} row(s) this collection covers
     * (V153) — best-effort, same reasoning as {@link #attributeCommission}: a
     * failure here must never roll back an already-committed payment review.
     */
    private void applyMembershipCharges(Payment payment) {
        try {
            membershipChargeService.applyPayment(payment);
        } catch (RuntimeException ex) {
            log.error("Failed to apply membership charges for payment {}", payment.getUuid(), ex);
        }
    }

    /**
     * Stamps {@link Member#getConfirmedAt()} the moment a member's <i>first</i>
     * payment is approved. A no-op if the member is already confirmed
     * (e.g. manually, via {@code POST /v1/admin/members/{uuid}/confirm} —
     * subsidized members never pay) or if this isn't their first approved
     * payment. {@code payment} was just moved to APPROVED, so a count of 1
     * here means this is the first one.
     */
    private void confirmMemberOnFirstApprovedPayment(Payment payment) {
        Member member = payment.getMembership().getMember();
        if (member.getConfirmedAt() != null) {
            return;
        }
        long approvedCount = paymentRepository.countApprovedByMemberId(member.getId());
        if (approvedCount == 1) {
            member.setConfirmedAt(Instant.now());
        }
    }

    /**
     * Fires the commission engine for the approved payment. Best-effort —
     * a failure to attribute (no promoter resolvable, calc error) is
     * logged and swallowed so the payment review stays committed. Admin
     * tooling can re-attribute via a future endpoint if necessary.
     */
    private void attributeCommission(Payment payment) {
        try {
            // Best-effort, same try/catch as the direct commission itself — a
            // hierarchy-override failure must never roll back the payment
            // approval (hub plan §2, HierarchyOverrideService.cascadeFrom).
            commissionService.calculateAndPersistFor(payment).ifPresent(hierarchyOverrideService::cascadeFrom);
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
    @Auditable(entity = "payment", action = AuditAction.UPDATE, uuidArgIndex = 0)
    public PaymentDto reject(UUID paymentUuid, PaymentRejectRequest request, UUID actorUserUuid) {
        Payment payment = findManaged(paymentUuid);
        if ("OUT".equals(payment.getDirection())) {
            throw new IllegalArgumentException("payment.collection.required");
        }
        ensurePending(payment);

        applyReview(payment, PaymentStatus.REJECTED, actorUserUuid, request.reason());

        if (payment.getMembership() != null) {
            validatorCacheService.evictForMembership(payment.getMembership());
        }
        dispatchNotification(payment, "payment-rejected", "email.payment.rejected.subject");
        notifyPromoterOfDecision(payment, "REJECTED");
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
    @Auditable(entity = "payment", action = AuditAction.UPDATE, uuidArgIndex = 0)
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

    /**
     * Snapshots the exchange rate at approval time (ADR 0015 §5/§7 Caso A)
     * when the payment settles against a membership denominated in a
     * different currency. Same currency → both columns stay {@code null},
     * no {@code exchange_rates} lookup. A missing rate degrades to
     * {@code null} rather than blocking the approval — per ADR 0015 §7,
     * this snapshot is informative/reporting, never a hard dependency of
     * the payment-review workflow.
     */
    private void snapshotExchangeRate(Payment payment) {
        var membershipCurrency = payment.getMembership().getCurrency();
        if (membershipCurrency == null || membershipCurrency.getId().equals(payment.getCurrency().getId())) {
            return;
        }
        try {
            var result = currencyConversionService.convert(
                    payment.getAmount(), payment.getCurrency(), membershipCurrency,
                    payment.getPaymentDate());
            payment.setExchangeRateUsed(result.rate());
            payment.setExchangeRateDate(result.rateDate());
        } catch (com.fenixcore.optibienestar360.modules.currency.exception.NoExchangeRateAvailableException noRate) {
            log.info("No exchange rate available to snapshot for payment {} ({} -> {}); leaving null",
                    payment.getUuid(), payment.getCurrency().getCode(), membershipCurrency.getCode());
        }
    }

    private static void ensurePending(Payment payment) {
        if (!PaymentStatus.PENDING.name().equals(payment.getStatus())) {
            throw new IllegalArgumentException("payment.review.not_pending");
        }
    }

    /**
     * Header review, mirrored onto every line (V117 §"Diseño de tablas": a
     * line can in principle be reviewed independently, but this flow always
     * has exactly one line today, so keeping both in lockstep here is the
     * simplest consistent behavior until a real multi-line review UI exists).
     */
    private void applyReview(Payment payment, PaymentStatus targetStatus,
                             UUID actorUserUuid, String reason) {
        User reviewer = userRepository.findByUuid(actorUserUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        Instant now = Instant.now();
        payment.setStatus(targetStatus.name());
        payment.setReviewedBy(reviewer);
        payment.setReviewedAt(now);
        payment.setReviewReason(reason);
        for (PaymentLine line : payment.getLines()) {
            line.setStatus(targetStatus.name());
            line.setReviewedBy(reviewer);
            line.setReviewedAt(now);
            line.setReviewReason(reason);
        }
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
        fileValidationService.validate(VISIBILITY, file);

        payment.setSupportFileName(file.getOriginalFilename());
        payment.setSupportFileContentType(file.getContentType());
        payment.setSupportFileSizeBytes(file.getSize());

        StorageService storage = storageProvider.getIfAvailable();
        if (storage == null) {
            log.info("R2 storage disabled — payment {} support file metadata captured but bytes discarded",
                    payment.getUuid());
            return;
        }

        // @PrePersist only assigns the UUID at flush time (and only if still
        // null) — this runs before the initial save() in register(), so it
        // must be assigned explicitly here to build the owner-scoped key.
        if (payment.getUuid() == null) {
            payment.setUuid(UUID.randomUUID());
        }
        String key = StorageKeyBuilder.build(VISIBILITY, OWNER_TABLE, payment.getUuid(), file.getOriginalFilename());
        try {
            storage.upload(key, file.getInputStream(), file.getSize(), file.getContentType());
            payment.setSupportFileUrl(key);
        } catch (IOException ex) {
            log.error("Failed to read support file stream for payment registration", ex);
            throw new IllegalArgumentException("payment.support.upload_failed");
        }
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
        Map<String, Object> vars = buildTemplateVars(payment, person);

        try {
            emailService.sendTemplated(to, subject, template, locale, vars);
        } catch (RuntimeException ex) {
            log.error("Failed to dispatch {} email for payment {}", template, payment.getUuid(), ex);
        }
    }

    /** Shared template-var shape for every payment notification (member, promoter or admin). */
    private static Map<String, Object> buildTemplateVars(Payment payment, Person person) {
        Map<String, Object> vars = new HashMap<>();
        vars.put("fullName", person != null ? Optional.ofNullable(person.getFullName()).orElse("") : "");
        vars.put("planName", payment.getMembership() != null ? payment.getMembership().getPlan().getName() : null);
        vars.put("amount", payment.getAmount());
        vars.put("currency", payment.getCurrency().getCode());
        // V117: method/reference moved to payment_lines — first (today, only) line.
        PaymentLine firstLine = payment.getLines().stream().findFirst().orElse(null);
        vars.put("paymentMethod", firstLine != null ? firstLine.getPaymentType().getCode() : null);
        vars.put("referenceNumber", firstLine != null ? firstLine.getReferenceNumber() : null);
        vars.put("paymentDate", payment.getPaymentDate());
        vars.put("inscription", payment.isInscription());
        vars.put("appliedPeriod", payment.getAppliedPeriod());
        vars.put("reviewReason", payment.getReviewReason());
        return vars;
    }

    /**
     * Notifies the paying affiliate's promoter (V153) of an approve/reject
     * decision — best-effort, same swallow-and-log policy as
     * {@link #dispatchNotification}: a missing promoter link, a promoter
     * without an email, or an SMTP failure must never affect the already-
     * committed review.
     */
    private void notifyPromoterOfDecision(Payment payment, String decision) {
        Promoter promoter = promoterFor(payment);
        if (promoter == null || promoter.getEmail() == null || promoter.getEmail().isBlank()) {
            return;
        }
        try {
            notificationChannelResolver.resolve(RecipientType.PROMOTER, "payment-decision-promoter");
            Locale locale = Locale.forLanguageTag("es");
            String subject = messageSource.getMessage("email.payment.decision.promoter.subject", null, locale);
            Map<String, Object> vars = buildTemplateVars(payment, personFor(payment));
            vars.put("decision", decision);
            emailService.sendTemplated(promoter.getEmail(), subject, "payment-decision-promoter", locale, vars);
        } catch (RuntimeException ex) {
            log.error("Failed to notify promoter of {} decision for payment {}", decision, payment.getUuid(), ex);
        }
    }

    /**
     * Notifies the affiliate's promoter and the {@code mail.admin} recipient
     * (V153) that a new collection was submitted and needs review —
     * best-effort, same swallow-and-log policy as {@link #dispatchNotification}.
     */
    private void notifySubmissionToPromoterAndAdmin(Payment payment) {
        try {
            Locale locale = Locale.forLanguageTag("es");
            String subject = messageSource.getMessage("email.payment.submitted.promoter_admin.subject", null, locale);
            Map<String, Object> vars = buildTemplateVars(payment, personFor(payment));

            Promoter promoter = promoterFor(payment);
            if (promoter != null && promoter.getEmail() != null && !promoter.getEmail().isBlank()) {
                notificationChannelResolver.resolve(RecipientType.PROMOTER, "payment-submitted-promoter-admin");
                emailService.sendTemplated(promoter.getEmail(), subject, "payment-submitted-promoter-admin", locale, vars);
            }
            if (adminEmail != null && !adminEmail.isBlank()) {
                notificationChannelResolver.resolve(RecipientType.ADMIN, "payment-submitted-promoter-admin");
                emailService.sendTemplated(adminEmail, subject, "payment-submitted-promoter-admin", locale, vars);
            }
        } catch (RuntimeException ex) {
            log.error("Failed to notify promoter/admin of new payment submission {}", payment.getUuid(), ex);
        }
    }

    private static Promoter promoterFor(Payment payment) {
        Membership membership = payment.getMembership();
        if (membership == null) return null;
        Member member = membership.getMember();
        return member != null ? member.getPromoter() : null;
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
