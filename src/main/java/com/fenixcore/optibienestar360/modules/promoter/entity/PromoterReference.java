package com.fenixcore.optibienestar360.modules.promoter.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
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

/**
 * Personal reference of an advisor (V180, dossier section IV). Plain contact
 * data — intentionally NOT a {@code persons} row: a reference is not a
 * participant of the system, just a name and a phone number to call.
 * The cédula pair is optional (both or neither, V180 CHECK).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "promoter_references")
@AttributeOverride(name = "id", column = @Column(name = "promoter_references_id", nullable = false, updatable = false))
public class PromoterReference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "promoter_id", nullable = false)
    private Promoter promoter;

    @Column(name = "first_name", length = 50, nullable = false)
    private String firstName;

    @Column(name = "last_name", length = 50, nullable = false)
    private String lastName;

    @Column(name = "document_type", length = 1)
    private String documentType;

    @Column(name = "document_number", length = 20)
    private String documentNumber;

    @Column(length = 30)
    private String phone;
}
