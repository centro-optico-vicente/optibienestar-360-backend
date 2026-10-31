package com.fenixcore.optibienestar360.modules.card.service;

import com.fenixcore.optibienestar360.modules.card.dto.DigitalCardDto;
import com.fenixcore.optibienestar360.modules.card.entity.DigitalCardRow;
import com.fenixcore.optibienestar360.modules.card.repository.DigitalCardRepository;
import com.fenixcore.optibienestar360.modules.member.entity.Beneficiary;
import com.fenixcore.optibienestar360.modules.member.repository.BeneficiaryRepository;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;
import java.util.Optional;
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
    private final BeneficiaryRepository beneficiaryRepository;

    public DigitalCardDto getForUser(UUID userUuid) {
        Optional<DigitalCardRow> own = repository.findByUserUuid(userUuid);
        if (own.isPresent()) {
            DigitalCardRow row = own.get();
            return new DigitalCardDto(
                    row.getMemberUuid(),
                    row.getFullName(),
                    row.getDocumentType(),
                    row.getDocumentNumber(),
                    row.getPlanName(),
                    row.getMembershipStatus(),
                    row.getNextDueDate(),
                    row.getMemberSince(),
                    qrFor(row.getMemberUuid()),
                    null,
                    null);
        }
        return beneficiaryRepository.findByUser_UuidAndActiveTrue(userUuid)
                .map(this::beneficiaryCard)
                .orElseThrow(() -> new NoSuchElementException("me.member.not_enrolled"));
    }

    /**
     * A beneficiary with portal access (V195): their own identity on the
     * holder's coverage — plan, status and due date come from the holder's
     * card, since the beneficiary is covered by that membership.
     */
    private DigitalCardDto beneficiaryCard(Beneficiary beneficiary) {
        UUID memberUuid = beneficiary.getMember().getUuid();
        DigitalCardRow holder = repository.findByMemberUuid(memberUuid)
                .orElseThrow(() -> new NoSuchElementException("me.member.not_enrolled"));
        Person person = beneficiary.getPerson();
        return new DigitalCardDto(
                memberUuid,
                person.getFullName(),
                person.getDocumentType(),
                person.getDocumentNumber(),
                holder.getPlanName(),
                holder.getMembershipStatus(),
                holder.getNextDueDate(),
                holder.getMemberSince(),
                // Their own UUID: the validator resolves it to them on the holder's coverage.
                qrFor(beneficiary.getUuid()),
                holder.getFullName(),
                beneficiary.getRelationship() != null ? beneficiary.getRelationship().name() : null);
    }

    /**
     * The QR encodes the member UUID (or the beneficiary UUID on a
     * beneficiary's card) — an ally scans it to resolve the coverage through
     * the validator without typing the cédula.
     */
    private String qrFor(UUID uuid) {
        return qrCodeGenerator.toPngDataUri(uuid.toString(), QR_SIZE_PX);
    }
}
