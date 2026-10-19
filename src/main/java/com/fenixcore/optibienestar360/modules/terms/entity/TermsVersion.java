package com.fenixcore.optibienestar360.modules.terms.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.core.entity.CodedEnum;
import com.fenixcore.optibienestar360.modules.ally.entity.Ally;
import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Objects;

/**
 * A single published (or scheduled) version of one contract kind
 * ({@link TermType}). Vigency by timestamp, same pattern as
 * {@code ExchangeRate} (V85) — the "current" row of a stream is the one
 * with the greatest {@code validFrom <= now()}; there is no
 * {@code validTo}, the next row's {@code validFrom} implicitly closes
 * the previous window. Never overwritten: an edit before it becomes
 * vigente mutates this row, but once vigente any change requires a new
 * row (see {@code TermsVersionService}).
 *
 * <p>Streams (V183): MEMBER and ADVISOR have a single stream per type.
 * ALLY templates may carry an optional scope ({@link #allyType},
 * {@link #segment}, {@link #ally}); each distinct scope combination is its
 * own stream, with its own current version. All scope fields {@code null}
 * = a general template.</p>
 *
 * <p>{@link #isPublic} only means "served by the public web endpoint"; who
 * is asked to accept a version is decided by {@code TermsAcceptanceService}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "terms_versions")
@AttributeOverride(name = "id", column = @Column(name = "terms_versions_id", nullable = false, updatable = false))
public class TermsVersion extends BaseEntity {

    /**
     * Contract kind. The Java constants are English; {@link TermType#code()} is the
     * Spanish value stored in {@code terms_versions.term_type} and exchanged
     * through the API (V139 CHECK, renamed ASESOR → ASESOR in V185):
     * <ul>
     *   <li>{@link TermType#MEMBER} ({@code AFILIADO}) — membership agreement, every user accepts it;</li>
     *   <li>{@link TermType#ADVISOR} ({@code ASESOR}) — sales advisor mandate;</li>
     *   <li>{@link TermType#ALLY} ({@code ALIADO}) — ally agreement template, accepted by the users
     *       of an ally whose active agreement links it.</li>
     * </ul>
     * Who is asked to accept each kind is decided by {@code TermsAcceptanceService}.
     */
    public enum TermType implements CodedEnum {
        MEMBER("AFILIADO"),
        ADVISOR("ASESOR"),
        ALLY("ALIADO");

        private final String code;

        TermType(String code) {
            this.code = code;
        }

        @Override
        @JsonValue
        public String code() {
            return code;
        }

        @JsonCreator
        public static TermType fromCode(String code) {
            return CodedEnum.fromCode(TermType.class, code);
        }
    }

    @Convert(converter = TermTypeConverter.class)
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

    // ─── ALLY template scope (V183) — all null = general template ────────

    /** Only allies of this type may sign the template; {@code null} = any type. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ally_type_id")
    private AllyType allyType;

    /** Only allies whose types belong to this segment may sign it; {@code null} = any segment. */
    @Enumerated(EnumType.STRING)
    @Column(length = 3)
    private AllyType.Segment segment;

    /** Template made for this one ally; {@code null} = any ally. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ally_id")
    private Ally ally;

    /** {@code true} when no scope field is set (always the case for MEMBER / ADVISOR). */
    public boolean hasNoScope() {
        return allyType == null && segment == null && ally == null;
    }

    /** Same versioned stream: same type and the same scope combination. */
    public boolean sameStreamAs(TermType otherType, Long otherAllyTypeId, AllyType.Segment otherSegment,
                                Long otherAllyId) {
        return termType == otherType
                && Objects.equals(idOf(allyType), otherAllyTypeId)
                && segment == otherSegment
                && Objects.equals(idOf(ally), otherAllyId);
    }

    /** {@link #sameStreamAs(TermType, Long, AllyType.Segment, Long)} against another version. */
    public boolean sameStreamAs(TermsVersion other) {
        return sameStreamAs(other.getTermType(), idOf(other.getAllyType()), other.getSegment(),
                idOf(other.getAlly()));
    }

    private static Long idOf(AllyType type) {
        return type == null ? null : type.getId();
    }

    private static Long idOf(Ally a) {
        return a == null ? null : a.getId();
    }
}
