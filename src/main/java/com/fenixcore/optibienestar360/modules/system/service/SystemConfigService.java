package com.fenixcore.optibienestar360.modules.system.service;

import com.fenixcore.optibienestar360.modules.system.entity.SystemConfig;
import com.fenixcore.optibienestar360.modules.system.repository.SystemConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemConfigService {

    public static final String DEFAULT_REPORT_FOOTER = "Creado desde compañía de ejemplo";

    private final SystemConfigRepository systemConfigRepository;

    public SystemConfigService(SystemConfigRepository systemConfigRepository) {
        this.systemConfigRepository = systemConfigRepository;
    }

    /**
     * Gets the active SystemConfig singleton instance or creates a new empty instance.
     */
    @Transactional(readOnly = true)
    public SystemConfig getSystemConfig() {
        return systemConfigRepository.findFirstByActiveTrue()
                .orElseGet(SystemConfig::new);
    }

    /**
     * Retrieves the current report footer text, returning null/empty if not configured.
     */
    @Transactional(readOnly = true)
    public String getReportFooter() {
        return systemConfigRepository.findFirstByActiveTrue()
                .map(SystemConfig::getReportFooter)
                .orElse(null);
    }

    /**
     * Updates the singleton system configuration report footer.
     */
    @Transactional
    public SystemConfig updateReportFooter(String reportFooter) {
        SystemConfig config = systemConfigRepository.findFirstByActiveTrue()
                .orElseGet(SystemConfig::new);

        config.setReportFooter(reportFooter != null && !reportFooter.isBlank() ? reportFooter.trim() : null);
        return systemConfigRepository.save(config);
    }
}
