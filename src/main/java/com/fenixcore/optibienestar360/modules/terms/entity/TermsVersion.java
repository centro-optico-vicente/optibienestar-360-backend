package com.fenixcore.optibienestar360.modules.terms.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * A single published (or scheduled) version of the T&C for one role
 * ({@link TermType}). Vigency by timestamp, same pattern as
 * {@code ExchangeRate} (V85) — the "current" row for a type is the one
 * with the greatest {@code validFrom <= now()}; there is no
 * {@code validTo}, the next row's {@code validFrom} implicitly closes
 * the previous window. Never overwritten: an edit before it becomes
 * vigente mutates this row, but once vigente any change requires a new
 * row (see {@code TermsVersionService}).
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "terms_versions")
@AttributeOverride(name = "id", column = @Column(name = "terms_versions_id", nullable = false, updatable = false))
public class TermsVersion extends BaseEntity {

    /** Literally the role name it applies to (see V6 roles) — no mapping table needed. */
    public enum TermType { AFILIADO, PROMOTOR, ALIADO }

    @Enumerated(EnumType.STRING)
    @Column(name = "term_type", nullable = false, length = 20)
    private TermType termType;

    @Column(length = 200, nullable = false)
    private String title;

    @Column(name = "content_markdown", columnDefinition = "TEXT", nullable = false)
    private String contentMarkdown;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic = false;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;
}
