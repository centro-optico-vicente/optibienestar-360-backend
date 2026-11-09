package com.fenixcore.optibienestar360.modules.auth.service;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Invites a user created by staff without a password (hub ADR 0037 §5): emails
 * a link to create it, reusing the password-setup token of
 * {@link AuthService#issuePasswordSetupLink}. The link lives
 * {@code app.user-invitations.ttl-hours} (72 by default); after that the person
 * uses "forgot my password" or asks for an access link.
 */
@Service
@RequiredArgsConstructor
public class UserInvitationService {

    static final String TEMPLATE = "user-invitation";
    private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("es-VE");

    private final UserRepository userRepository;
    private final AuthService authService;
    private final EmailService emailService;
    private final MessageSource messageSource;

    @Value("${app.user-invitations.ttl-hours:72}")
    private long ttlHours = 72;

    @Transactional
    public void invite(UUID userUuid) {
        User user = userRepository.findByUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("user.not_found"));
        Locale locale = resolveLocale(user.getPerson().getLocale());
        Duration ttl = Duration.ofHours(ttlHours > 0 ? ttlHours : 72);
        String setupUrl = authService.issuePasswordSetupLink(user, ttl, locale);
        emailService.sendTemplated(
                user.getEmail(),
                messageSource.getMessage("email.user_invitation.subject", null, locale),
                TEMPLATE,
                locale,
                Map.of(
                        "fullName", user.getPerson().getFullName() != null ? user.getPerson().getFullName() : "",
                        "setupUrl", setupUrl,
                        "ttlHours", ttl.toHours()));
    }

    private static Locale resolveLocale(String tag) {
        if (tag == null || tag.isBlank()) {
            return DEFAULT_LOCALE;
        }
        Locale parsed = Locale.forLanguageTag(tag);
        return parsed.getLanguage().isEmpty() ? DEFAULT_LOCALE : parsed;
    }
}
