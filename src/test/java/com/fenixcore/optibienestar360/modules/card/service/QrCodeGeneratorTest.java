package com.fenixcore.optibienestar360.modules.card.service;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link QrCodeGenerator} — verifies a real PNG data URI is
 * produced (ZXing is on the classpath and encodes without error).
 */
class QrCodeGeneratorTest {

    private final QrCodeGenerator generator = new QrCodeGenerator();

    @Test
    void toPngDataUri_producesDecodablePng() {
        String uri = generator.toPngDataUri(UUID.randomUUID().toString(), 200);

        assertThat(uri).startsWith("data:image/png;base64,");
        byte[] png = Base64.getDecoder().decode(uri.substring("data:image/png;base64,".length()));
        assertThat(png).isNotEmpty();
        // PNG magic number: 0x89 'P' 'N' 'G'
        assertThat(png[0] & 0xFF).isEqualTo(0x89);
        assertThat(png[1]).isEqualTo((byte) 'P');
        assertThat(png[2]).isEqualTo((byte) 'N');
        assertThat(png[3]).isEqualTo((byte) 'G');
    }
}
