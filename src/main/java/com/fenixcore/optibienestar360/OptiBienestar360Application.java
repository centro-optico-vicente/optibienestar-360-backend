package com.fenixcore.optibienestar360;

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
		Locale.setDefault(Locale.forLanguageTag("es-VE"));
		TimeZone.setDefault(TimeZone.getTimeZone("America/Caracas"));

		SpringApplication.run(OptiBienestar360Application.class, args);
	}

}
