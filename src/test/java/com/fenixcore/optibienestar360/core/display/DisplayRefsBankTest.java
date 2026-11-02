package com.fenixcore.optibienestar360.core.display;

import com.fenixcore.optibienestar360.modules.bank.entity.Bank;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DisplayRefsBankTest {

    @Test
    void bank_isShownWithItsCodeAndCommercialName_notTheLegalOne() {
        Bank bank = new Bank();
        bank.setUuid(UUID.randomUUID());
        bank.setCode("0105");
        bank.setName("Mercantil Banco, C.A. Banco Universal");
        bank.setShortName("Mercantil");

        DisplayRef ref = DisplayRefs.ref(bank);

        assertThat(ref.uuid()).isEqualTo(bank.getUuid());
        assertThat(ref.code()).isEqualTo("0105");
        assertThat(ref.name()).isEqualTo("Mercantil");
    }

    @Test
    void noBank_isNoRef() {
        assertThat(DisplayRefs.ref((Bank) null)).isNull();
    }
}
