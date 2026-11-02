package com.fenixcore.optibienestar360.core.config;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The business clock: system time in the app's zone ({@link AppTimeZone},
 * America/Caracas by default, ADR 0010). Injected where "now" matters for a
 * rule (e.g. the public "open now" filter) so tests can pin it with
 * {@link Clock#fixed}.
 */
@Configuration
public class BusinessClockConfig {

    @Bean
    public Clock businessClock() {
        return Clock.system(AppTimeZone.ZONE);
    }
}
