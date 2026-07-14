package com.fenixcore.optibienestar360.modules.contact;

import com.fenixcore.optibienestar360.common.service.EmailService;
import com.fenixcore.optibienestar360.modules.contact.dto.ContactRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactMessageRepository repository;
    private final EmailService emailService;
    private final MessageSource messageSource;

    @Value("${mail.admin}")
    private String adminEmail;

    @Transactional
    public void submit(ContactRequestDto dto) {
        ContactMessage entity = new ContactMessage();
        entity.setName(dto.name());
        entity.setEmail(dto.email());
        entity.setPhone(dto.phone());
        entity.setSubject(dto.subject());
        entity.setMessage(dto.message());
        entity.setStatus("NEW");
        repository.save(entity);

        log.info("Contact message saved from {}", dto.email());

        // Public form: no authenticated user, so the request locale
        // (Accept-Language → app default) is the only signal available.
        Locale requestLocale = LocaleContextHolder.getLocale();
        String subject = messageSource.getMessage(
                "email.contact.subject", new Object[]{dto.subject()}, requestLocale);

        // Email is best-effort — DB save is the source of truth
        emailService.sendTemplated(
                adminEmail,
                subject,
                "contact-form-received",
                requestLocale,
                Map.of("contact", dto)
        );
    }
}
