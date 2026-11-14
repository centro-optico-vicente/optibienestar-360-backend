package com.fenixcore.optibienestar360.modules.notification.service;

import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import com.fenixcore.optibienestar360.modules.notification.dto.UserNotificationDto;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification;
import com.fenixcore.optibienestar360.modules.notification.entity.UserNotification.Category;
import com.fenixcore.optibienestar360.modules.notification.repository.UserNotificationRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * The user's side of the in-app inbox (V259): list, unread count, mark as read.
 * Every method is scoped to the caller (resolved from the JWT subject); another
 * user's row is indistinguishable from a missing one (404). Expired rows
 * ({@code expires_at <= now}) are left out of the list and the count.
 * Writing new rows is {@link UserNotificationPublisher}'s job.
 */
@Service
@RequiredArgsConstructor
public class UserNotificationService {

    /** Newest first; the id breaks ties between rows created in the same instant. */
    static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
    static final int MAX_PAGE_SIZE = 100;

    private final UserNotificationRepository repository;
    private final UserRepository userRepository;

    /**
     * The caller's notifications, newest first. {@code unreadOnly} keeps only
     * the unread ones; {@code category} (optional, case-insensitive) filters by
     * {@link Category}. The client's sort is ignored: the inbox order is fixed.
     */
    @Transactional(readOnly = true)
    public Page<UserNotificationDto> listMine(UUID userUuid, boolean unreadOnly, String category, Pageable pageable) {
        Long userId = userId(userUuid);
        Category cat = parseCategory(category);
        return repository.findAll(inbox(userId, unreadOnly, cat, Instant.now()), fixedOrder(pageable))
                .map(UserNotificationService::toDto);
    }

    /** Unread, non-expired notifications of the caller. */
    @Transactional(readOnly = true)
    public long unreadCount(UUID userUuid) {
        return repository.countUnread(userId(userUuid), Instant.now());
    }

    /** Marks one of the caller's notifications as read. Idempotent; 404 when it is not theirs. */
    @Transactional
    public void markRead(UUID userUuid, UUID notificationUuid) {
        UserNotification n = repository.findByUuidAndRecipientUserIdAndActiveTrue(notificationUuid, userId(userUuid))
                .orElseThrow(() -> new NoSuchElementException("user_notification.not_found"));
        if (n.getReadAt() == null) {
            n.setReadAt(Instant.now());
        }
    }

    /** Marks every unread notification of the caller as read; returns how many changed. */
    @Transactional
    public int markAllRead(UUID userUuid) {
        return repository.markAllRead(userId(userUuid), Instant.now());
    }

    // ─── Internals ────────────────────────────────────────────────────────────

    static Specification<UserNotification> inbox(Long userId, boolean unreadOnly, Category category, Instant now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("recipientUserId"), userId));
            predicates.add(cb.isTrue(root.<Boolean>get("active")));
            predicates.add(cb.or(
                    cb.isNull(root.get("expiresAt")),
                    cb.greaterThan(root.<Instant>get("expiresAt"), now)));
            if (unreadOnly) {
                predicates.add(cb.isNull(root.get("readAt")));
            }
            if (category != null) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    static Pageable fixedOrder(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequest.of(0, MAX_PAGE_SIZE, NEWEST_FIRST);
        }
        return PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE), NEWEST_FIRST);
    }

    static Category parseCategory(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return Category.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("user_notification.category.invalid");
        }
    }

    static UserNotificationDto toDto(UserNotification n) {
        return new UserNotificationDto(
                n.getUuid(),
                n.getType(),
                n.getCategory(),
                n.getSeverity(),
                n.getParams() != null ? new HashMap<>(n.getParams()) : new HashMap<>(),
                n.getFallbackTitle(),
                n.getFallbackBody(),
                n.getActionUrl(),
                n.getCreatedAt(),
                n.getReadAt(),
                n.getReadAt() != null);
    }

    private Long userId(UUID userUuid) {
        return userRepository.findIdByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
    }
}
