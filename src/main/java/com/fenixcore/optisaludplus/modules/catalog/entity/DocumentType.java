package com.fenixcore.optisaludplus.modules.catalog.entity;

import com.fenixcore.optisaludplus.core.entity.BaseAuditEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "document_types")
@AttributeOverride(name = "id", column = @Column(name = "document_types_id", nullable = false, updatable = false))
public class DocumentType extends BaseAuditEntity {

    @Column(length = 3, unique = true, nullable = false)
    private String code;

    @Column(length = 60, unique = true, nullable = false)
    private String name;

    @Column(length = 200)
    private String description;
}
