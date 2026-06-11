package com.fenixcore.optisaludplus.modules.validator.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fenixcore.optisaludplus.modules.member.entity.Member;
import com.fenixcore.optisaludplus.modules.member.repository.MemberRepository;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership;
import com.fenixcore.optisaludplus.modules.membership.entity.Membership.LifecycleStatus;
import com.fenixcore.optisaludplus.modules.membership.entity.Plan;
import com.fenixcore.optisaludplus.modules.membership.repository.MembershipRepository;
import com.fenixcore.optisaludplus.modules.person.entity.Person;
import com.fenixcore.optisaludplus.modules.person.repository.PersonRepository;
import com.fenixcore.optisaludplus.modules.validator.dto.ValidationResultDto;
import com.fenixcore.optisaludplus.modules.validator.dto.ValidationResultDto.ValidationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Realtime solvency oracle for the ally portal.
 *
 * <p>The ally counter operator hits {@code GET /v1/ally/validate/{document}}
 * before applying any benefit. Per the vertical-7 spec the call must
 * return in p95 &lt; 200ms, which is why every successful answer is cached
 * in Redis for 60 seconds under the canonical key
 * {@code validator:document:{type}:{number}}. The
 * {@link ValidatorCacheService} eviction hooks (membership status changes,
 * payment approve / reject) keep the cache honest in the meantime.</p>
 *
 * <p><b>Cache key shape</b> is the same one
 * {@link ValidatorCacheService#keyFor(String, String)} produces — single
 * source of truth shared between producer (this service) and invalidator.
 * The bullet text says {@code validator:{document}}; we refine it to
 * {@code validator:document:{type}:{number}} so it's impossible to confuse
 * a {@code V} document with an {@code E} document that shares the same
 * number.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class ValidatorService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    /** Accepts the V12345678 / V-12345678 / E-12345678 family. Case-insensitive. */
    private static final Pattern DOCUMENT_PATTERN = Pattern.compile("^([VEvVe])-?(\\d{1,20})$");

    private final PersonRepository personRepository;
    private final MemberRepository memberRepository;
    private final MembershipRepository membershipRepository;
    private final ValidatorCacheService cacheService;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final ObjectMapper objectMapper;

    public ValidationResultDto validate(String document) {
        DocumentParts parts = parse(document);

        Optional<ValidationResultDto> cached = readCache(parts);
        if (cached.isPresent()) {
            return cached.get();
        }

        ValidationResultDto computed = resolve(parts);
        writeCache(parts, computed);
        return computed;
    }

    // ─── Lookup ─────────────────────────────────────────────────────────────

    private ValidationResultDto resolve(DocumentParts parts) {
        Optional<Person> personOpt = personRepository
                .findByDocumentTypeAndDocumentNumber(parts.type, parts.number);
        if (personOpt.isEmpty() || !personOpt.get().isActive()) {
            return base(ValidationStatus.NOT_FOUND, parts).build(false);
        }
        Person person = personOpt.get();

        Optional<Member> memberOpt = memberRepository.findByPersonId(person.getId());
        if (memberOpt.isEmpty() || !memberOpt.get().isActive()) {
            return base(ValidationStatus.NOT_ENROLLED, parts)
                    .withPerson(person)
                    .build(false);
        }
        Member member = memberOpt.get();

        Optional<Membership> membershipOpt = membershipRepository.findFirstByMemberIdAndActiveTrue(member.getId());
        if (membershipOpt.isEmpty()) {
            return base(ValidationStatus.NO_ACTIVE_MEMBERSHIP, parts)
                    .withPerson(person)
                    .withMember(member)
                    .build(false);
        }
        Membership membership = membershipOpt.get();

        ValidationStatus status = mapLifecycle(membership.getStatus());
        return base(status, parts)
                .withPerson(person)
                .withMember(member)
                .withMembership(membership)
                .build(false);
    }

    private static ValidationStatus mapLifecycle(String lifecycleStatus) {
        if (lifecycleStatus == null) return ValidationStatus.NO_ACTIVE_MEMBERSHIP;
        try {
            LifecycleStatus parsed = LifecycleStatus.valueOf(lifecycleStatus);
            return switch (parsed) {
                case ACTIVE    -> ValidationStatus.ACTIVE;
                case SUSPENDED -> ValidationStatus.SUSPENDED;
                case EXPIRED   -> ValidationStatus.EXPIRED;
                case CANCELED  -> ValidationStatus.CANCELED;
            };
        } catch (IllegalArgumentException ex) {
            return ValidationStatus.NO_ACTIVE_MEMBERSHIP;
        }
    }

    // ─── Cache ──────────────────────────────────────────────────────────────

    private Optional<ValidationResultDto> readCache(DocumentParts parts) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) return Optional.empty();

        String key = cacheService.keyFor(parts.type, parts.number);
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) return Optional.empty();
            ValidationResultDto deserialized = objectMapper.readValue(json, ValidationResultDto.class);
            // Echo the same payload but flip cached=true so the ally portal
            // sees that this came from Redis without us double-storing the
            // flag in the cached value.
            return Optional.of(withCachedFlag(deserialized, true));
        } catch (JsonProcessingException ex) {
            log.warn("Validator cache deserialization failed for {}: {}", key, ex.getMessage());
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Validator cache read failed for {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    private void writeCache(DocumentParts parts, ValidationResultDto result) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) return;

        String key = cacheService.keyFor(parts.type, parts.number);
        try {
            String json = objectMapper.writeValueAsString(result);
            redis.opsForValue().set(key, json, CACHE_TTL);
        } catch (JsonProcessingException ex) {
            log.warn("Validator cache serialization failed for {}: {}", key, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("Validator cache write failed for {}: {}", key, ex.getMessage());
        }
    }

    private static ValidationResultDto withCachedFlag(ValidationResultDto source, boolean cached) {
        return new ValidationResultDto(
                source.status(), source.documentType(), source.documentNumber(),
                source.memberUuid(), source.memberFullName(),
                source.planUuid(), source.planCode(), source.planName(),
                source.membershipUuid(), source.enrolledAt(), source.nextDueDate(),
                source.lastPaidThrough(), source.gracePeriodDays(),
                cached);
    }

    // ─── Document parsing ──────────────────────────────────────────────────

    private static DocumentParts parse(String document) {
        if (document == null) throw new IllegalArgumentException("validator.document.invalid");
        Matcher matcher = DOCUMENT_PATTERN.matcher(document.trim());
        if (!matcher.matches()) throw new IllegalArgumentException("validator.document.invalid");
        return new DocumentParts(matcher.group(1).toUpperCase(), matcher.group(2));
    }

    private record DocumentParts(String type, String number) {}

    // ─── Result builder ────────────────────────────────────────────────────

    private static Builder base(ValidationStatus status, DocumentParts parts) {
        Builder b = new Builder();
        b.status = status;
        b.documentType = parts.type;
        b.documentNumber = parts.number;
        return b;
    }

    private static final class Builder {
        ValidationStatus status;
        String documentType;
        String documentNumber;
        java.util.UUID memberUuid;
        String memberFullName;
        java.util.UUID planUuid;
        String planCode;
        String planName;
        java.util.UUID membershipUuid;
        java.time.LocalDate enrolledAt;
        java.time.LocalDate nextDueDate;
        java.time.LocalDate lastPaidThrough;
        Integer gracePeriodDays;

        Builder withPerson(Person p) {
            this.memberFullName = p.getFullName();
            return this;
        }

        Builder withMember(Member m) {
            this.memberUuid = m.getUuid();
            return this;
        }

        Builder withMembership(Membership m) {
            this.membershipUuid = m.getUuid();
            this.enrolledAt = m.getEnrolledAt();
            this.nextDueDate = m.getNextDueDate();
            this.lastPaidThrough = m.getLastPaidThrough();
            this.gracePeriodDays = m.getGracePeriodDays();
            Plan plan = m.getPlan();
            if (plan != null) {
                this.planUuid = plan.getUuid();
                this.planCode = plan.getCode();
                this.planName = plan.getName();
            }
            return this;
        }

        ValidationResultDto build(boolean cached) {
            return new ValidationResultDto(
                    status, documentType, documentNumber,
                    memberUuid, memberFullName,
                    planUuid, planCode, planName,
                    membershipUuid, enrolledAt, nextDueDate, lastPaidThrough, gracePeriodDays,
                    cached);
        }
    }
}
