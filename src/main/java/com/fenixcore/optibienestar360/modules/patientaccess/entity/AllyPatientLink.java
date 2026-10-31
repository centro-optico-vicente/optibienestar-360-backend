package com.fenixcore.optibienestar360.modules.patientaccess.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import com.fenixcore.optibienestar360.modules.member.entity.Member;
import com.fenixcore.optibienestar360.modules.person.entity.Person;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Doctor–patient link (V215, hub ADR 0032). Binds the holder person of a B2P
 * ally of the MEDICAL area (the doctor) to a member, so the doctor can read
 * the member's medical history from any of their sites while the link is
 * {@link LinkStatus#ACTIVE}. Opened by a QR scan at the counter; the member
 * revokes it. A new scan after a revocation opens a new row — revoked rows
 * are kept as history.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "ally_patient_links")
@AttributeOverride(name = "id", column = @Column(name = "ally_patient_links_id", nullable = false, updatable = false))
public class AllyPatientLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_person_id", nullable = false)
    private Person doctorPerson;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    /** The site (ally) where the scan happened. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_at_ally_id", nullable = false)
    private Ally createdAtAlly;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    public boolean isLinkActive() {
        return LinkStatus.ACTIVE.name().equals(getStatus());
    }

    public void revoke(UUID actorUuid) {
        setStatus(LinkStatus.REVOKED.name());
        setRevokedAt(Instant.now());
        setRevokedBy(actorUuid);
    }

    public enum LinkStatus { ACTIVE, REVOKED }
}
