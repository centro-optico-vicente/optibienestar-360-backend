package com.fenixcore.optibienestar360.modules.card.service;

import com.fenixcore.optibienestar360.modules.card.dto.DigitalCardDto;
import com.fenixcore.optibienestar360.modules.card.entity.DigitalCardRow;
import com.fenixcore.optibienestar360.modules.card.repository.DigitalCardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Builds the affiliate digital card for the authenticated user: reads the
 * denormalized {@link DigitalCardRow} (V39 view) by user account and pairs it
 * with a freshly-generated QR image. A user with no active member row 404s
 * with {@code me.member.not_enrolled} — the same contract as
 * {@code GET /v1/me/member}, so staff users and soft-deleted enrollments are
 * indistinguishable to the caller.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DigitalCardService {

    private static final int QR_SIZE_PX = 240;

    private final DigitalCardRepository repository;
    private final QrCodeGenerator qrCodeGenerator;

    public DigitalCardDto getForUser(UUID userUuid) {
        DigitalCardRow row = repository.findByUserUuid(userUuid)
                .orElseThrow(() -> new NoSuchElementException("me.member.not_enrolled"));

        // The QR encodes the member UUID — an ally scans it to resolve the
        // holder through the validator without typing the cédula.
        String qr = qrCodeGenerator.toPngDataUri(row.getMemberUuid().toString(), QR_SIZE_PX);

        return new DigitalCardDto(
                row.getMemberUuid(),
                row.getFullName(),
                row.getDocumentType(),
                row.getDocumentNumber(),
                row.getPlanName(),
                row.getMembershipStatus(),
                row.getNextDueDate(),
                row.getMemberSince(),
                qr);
    }
}
