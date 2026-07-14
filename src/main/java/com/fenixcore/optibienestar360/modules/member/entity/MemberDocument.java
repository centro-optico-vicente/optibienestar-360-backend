package com.fenixcore.optibienestar360.modules.member.entity;

import com.fenixcore.optibienestar360.core.entity.BaseEntity;
import com.fenixcore.optibienestar360.modules.auth.entity.User;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
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

/**
 * File uploaded for a {@link Member} — cédula scans, proof of residence,
 * member photo for the digital card, income proof for subsidy requests,
 * etc. The file content lives in Cloudflare R2; this row carries the
 * storage key + metadata.
 *
 * <p>{@link DocumentType} mirrors the V17 CHECK enum verbatim. New types
 * (LICENSE, BIRTH_CERTIFICATE, …) require a migration to extend the CHECK
 * constraint — kept small so the upload form can show a fixed dropdown.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "member_documents")
@AttributeOverride(name = "id", column = @Column(name = "member_documents_id", nullable = false, updatable = false))
public class MemberDocument extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", length = 50, nullable = false)
    private DocumentType documentType;

    @Column(name = "file_url", length = 500, nullable = false)
    private String fileUrl;

    @Column(name = "file_name", length = 255, nullable = false)
    private String fileName;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "mime_type", length = 100, nullable = false)
    private String mimeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private User uploadedBy;

    public enum DocumentType {
        ID_FRONT, ID_BACK, PROOF_OF_RESIDENCE, MEDICAL_HISTORY,
        MEMBER_PHOTO, INCOME_PROOF, OTHER
    }
}
