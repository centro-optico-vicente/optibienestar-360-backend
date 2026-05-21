package com.fenixcore.optisaludplus.modules.contact;

import com.fenixcore.optisaludplus.common.service.EmailService;
import com.fenixcore.optisaludplus.modules.contact.dto.ContactRequestDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ContactService {

    private final ContactMessageRepository repository;
    private final EmailService emailService;

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

        // Email is best-effort — DB save is the source of truth
        emailService.sendTemplated(
                adminEmail,
                "Nuevo contacto: " + dto.subject(),
                "contact-form-received",
                Map.of("contact", dto)
        );
    }
}
