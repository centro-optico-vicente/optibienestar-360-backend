package com.fenixcore.optisaludplus.modules.catalog.entity;

import com.fenixcore.optisaludplus.core.entity.BaseAuditEntity;
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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "states")
@AttributeOverride(name = "id", column = @Column(name = "states_id", nullable = false, updatable = false))
public class State extends BaseAuditEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "country_id", nullable = false)
    private Country country;

    @Column(length = 10, nullable = false)
    private String code;

    @Column(length = 100, nullable = false)
    private String name;
}
