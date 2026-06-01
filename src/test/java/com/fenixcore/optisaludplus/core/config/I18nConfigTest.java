package com.fenixcore.optisaludplus.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.test.context.ActiveProfiles;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class I18nConfigTest {

    @Autowired
    private MessageSource messageSource;

    @Test
    void resolves_key_in_spanish_bundle() {
        String msg = messageSource.getMessage("auth.credentials.invalid", null, Locale.forLanguageTag("es"));
        assertThat(msg).isEqualTo("Credenciales incorrectas");
    }

    @Test
    void resolves_key_in_english_bundle() {
        String msg = messageSource.getMessage("auth.credentials.invalid", null, Locale.forLanguageTag("en"));
        assertThat(msg).isEqualTo("Invalid credentials");
    }

    @Test
    void es_VE_locale_falls_back_to_es_bundle() {
        // ResourceBundle chain: messages_es_VE (no existe) → messages_es (existe).
        String msg = messageSource.getMessage("auth.credentials.invalid", null, new Locale("es", "VE"));
        assertThat(msg).isEqualTo("Credenciales incorrectas");
    }

    @Test
    void unsupported_locale_falls_back_to_canonical_messages_properties() {
        // pt-BR: no hay messages_pt_BR ni messages_pt → cae a messages.properties (EN canónico).
        String msg = messageSource.getMessage("auth.credentials.invalid", null, Locale.forLanguageTag("pt-BR"));
        assertThat(msg).isEqualTo("Invalid credentials");
    }

    @Test
    void validation_messages_bundle_is_part_of_basename_chain() {
        // Las claves de Jakarta Bean Validation (ValidationMessages*.properties) viven
        // bajo el mismo MessageSource gracias a spring.messages.basename=messages,ValidationMessages.
        String msg = messageSource.getMessage("validation.iso_code.alpha2", null, Locale.forLanguageTag("es"));
        assertThat(msg).isNotBlank();
    }
}
