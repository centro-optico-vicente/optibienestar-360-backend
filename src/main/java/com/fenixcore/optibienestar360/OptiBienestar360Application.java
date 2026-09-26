package com.fenixcore.optibienestar360;

import com.fenixcore.optibienestar360.core.util.AppTimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Locale;
import java.util.TimeZone;

@SpringBootApplication
public class OptiBienestar360Application {

	public static void main(String[] args) {
		// Pin JVM-wide defaults per ADR 0010 (Venezuela as the primary
		// market). Forces every library (Hibernate, log4j, MessageFormat,
		// java.time formatters that omit an explicit Locale, …) to behave
		// the same regardless of the JVM's host locale. Must run BEFORE
		// SpringApplication.run so the Spring context boots with the right
		// defaults; `spring.web.locale` and `spring.jackson.time-zone` from
		// application.properties cover the request/response paths on top.
		//
		// The timezone is read from AppTimeZone (env TZ, falls back to
		// America/Caracas) instead of a hardcoded literal (hub plan
		// competitive-commission-rules, Fase A, H1) — deployment/docker-compose.yaml
		// already sets TZ on every container from TIME_ZONE, so this makes
		// the JVM-wide default actually follow that env var instead of
		// silently staying on Caracas regardless of what TZ says.
		Locale.setDefault(Locale.forLanguageTag("es-VE"));
		TimeZone.setDefault(TimeZone.getTimeZone(AppTimeZone.ZONE));

		SpringApplication.run(OptiBienestar360Application.class, args);
	}

}
