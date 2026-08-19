package com.fenixcore.optibienestar360.modules.system.dto;

import jakarta.validation.constraints.Size;

public record UpdateSystemConfigRequest(
        @Size(max = 500, message = "system_config.report_footer.max_size")
        String reportFooter
) {}
