package com.fenixcore.optibienestar360.modules.member.event;

import com.fenixcore.optibienestar360.common.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Locale;

/**
 * Emails the beneficiary their portal invitation once the grant commits, so a
 * rolled-back grant never sends a link to an account that does not exist. A
 * mail failure is logged, not rethrown: the holder can resend the invitation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BeneficiaryAccessNotifier {

    private static final Locale LOCALE = Locale.forLanguageTag("es");

    private final EmailService emailService;
    private final MessageSource messageSource;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onInvited(BeneficiaryAccessInvitedEvent event) {
        try {
            String subject = messageSource.getMessage("email.beneficiary_access.subject", null, LOCALE);
            String body = messageSource.getMessage("email.beneficiary_access.body",
                    new Object[] {event.beneficiaryName(), event.holderName(), event.activationUrl()}, LOCALE);
            emailService.sendSimple(event.email(), subject, body);
        } catch (RuntimeException ex) {
            log.error("Failed to email beneficiary access invitation to {}", event.email(), ex);
        }
    }
}
