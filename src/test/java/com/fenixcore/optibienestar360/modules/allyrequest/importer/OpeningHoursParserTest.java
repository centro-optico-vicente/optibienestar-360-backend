package com.fenixcore.optibienestar360.modules.allyrequest.importer;

import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpeningHoursParserTest {

    @Test
    void spansListsAndSeveralRanges() {
        List<OpeningHoursParser.Range> ranges =
                OpeningHoursParser.parse("L-V 08:00-12:00, 14:00-18:00; S 8:00-12:00");

        assertThat(ranges).hasSize(11);
        assertThat(ranges.getFirst()).isEqualTo(
                new OpeningHoursParser.Range((short) 1, LocalTime.of(8, 0), LocalTime.of(12, 0)));
        assertThat(ranges.getLast()).isEqualTo(
                new OpeningHoursParser.Range((short) 6, LocalTime.of(8, 0), LocalTime.of(12, 0)));
    }

    @Test
    void aDayList() {
        assertThat(OpeningHoursParser.parse("l,x,v 14:00-18:00"))
                .extracting(OpeningHoursParser.Range::dayOfWeek)
                .containsExactly((short) 1, (short) 3, (short) 5);
    }

    @Test
    void blank_isNoHours() {
        assertThat(OpeningHoursParser.parse("  ")).isEmpty();
    }

    @Test
    void invalidText_isRejected() {
        assertThatThrownBy(() -> OpeningHoursParser.parse("Lunes a viernes")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OpeningHoursParser.parse("L 18:00-08:00")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> OpeningHoursParser.parse("V-L 08:00-12:00")).isInstanceOf(IllegalArgumentException.class);
    }
}
