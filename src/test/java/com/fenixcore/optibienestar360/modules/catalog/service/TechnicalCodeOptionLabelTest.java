package com.fenixcore.optibienestar360.modules.catalog.service;

import com.fenixcore.optibienestar360.modules.catalog.entity.AllyType;
import com.fenixcore.optibienestar360.modules.catalog.entity.MaritalStatus;
import com.fenixcore.optibienestar360.modules.catalog.entity.Profession;
import com.fenixcore.optibienestar360.modules.catalog.entity.PromoterType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADR LOC-0013, rule 5: catalogs whose code is a technical {@code SNAKE_CASE}
 * identifier show only the name in their {@code /options} label — the code
 * would clutter the select without helping the user tell options apart.
 */
class TechnicalCodeOptionLabelTest {

    @Test
    void maritalStatus_labelIsNameOnly() {
        MaritalStatus m = new MaritalStatus();
        m.setCode("SOLTERO");
        m.setName("Soltero");

        assertThat(MaritalStatusService.labelOf(m)).isEqualTo("Soltero");
    }

    @Test
    void profession_labelIsNameOnly() {
        Profession p = new Profession();
        p.setCode("INGENIERIA_CIVIL");
        p.setName("Ingeniería civil");

        assertThat(ProfessionService.labelOf(p)).isEqualTo("Ingeniería civil");
    }

    @Test
    void allyType_labelIsNameOnly() {
        AllyType a = new AllyType();
        a.setCode("CENTRO_OPTICO");
        a.setName("Centro óptico");

        assertThat(AllyTypeService.labelOf(a)).isEqualTo("Centro óptico");
    }

    @Test
    void advisorType_labelIsNameOnly() {
        PromoterType t = new PromoterType();
        t.setCode("INDEPENDIENTE");
        t.setName("Independiente");

        assertThat(PromoterTypeService.labelOf(t)).isEqualTo("Independiente");
    }
}
