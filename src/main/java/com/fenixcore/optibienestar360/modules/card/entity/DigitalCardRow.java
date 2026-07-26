package com.fenixcore.optibienestar360.modules.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Read-only projection of {@code digital_cards_view} (V39) — the denormalized
 * affiliate digital card. One row per active member, joining person identity,
 * the active membership + plan, and the linked user account so the card is a
 * single lookup by the authenticated caller.
 *
 * <p>{@code @Immutable} — never written through JPA; the view is maintained by
 * the underlying tables. The QR image is generated application-side and is not
 * part of this row.</p>
 */
@Getter
@NoArgsConstructor
@Entity
@Immutable
@Table(name = "digital_cards_view")
public class DigitalCardRow {

    @Id
    @Column(name = "member_uuid")
    private UUID memberUuid;

    @Column(name = "user_uuid")
    private UUID userUuid;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "document_type")
    private String documentType;

    @Column(name = "document_number")
    private String documentNumber;

    @Column(name = "plan_name")
    private String planName;

    @Column(name = "membership_status")
    private String membershipStatus;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "member_since")
    private LocalDate memberSince;
}
