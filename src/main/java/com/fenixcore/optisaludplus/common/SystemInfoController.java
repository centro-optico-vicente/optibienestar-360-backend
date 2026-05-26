package com.fenixcore.optisaludplus.common;

import com.fenixcore.optisaludplus.common.dto.SystemInfoResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Exposes lightweight metadata about the running backend. Public — no auth.
 *
 * <p>{@code GET /}, {@code GET /v1}, and {@code GET /v1/system-info} all return
 * the same payload. The first two cover root-level discovery; the third gives
 * a canonical, descriptive path for tooling / dashboards.
 */
@RestController
public class SystemInfoController {

    @Value("${spring.application.name:optisaludplus}")
    private String applicationName;

    @Value("${app.version:dev-0.0.1}")
    private String version;

    @Value("${app.version-date:}")
    private String versionDate;

    private final Environment environment;

    public SystemInfoController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping({"/", "/v1", "/v1/system-info"})
    public SystemInfoResponse info() {
        String resolvedDate = (versionDate == null || versionDate.isBlank())
                ? Instant.now().toString()
                : versionDate;
        String activeProfile = environment.getActiveProfiles().length == 0
                ? "default"
                : String.join(",", environment.getActiveProfiles());
        return new SystemInfoResponse(
                applicationName,
                version,
                resolvedDate,
                activeProfile,
                Instant.now()
        );
    }
}
