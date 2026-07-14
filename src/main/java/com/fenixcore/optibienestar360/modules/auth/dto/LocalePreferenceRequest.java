package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LocalePreferenceRequest(
        @NotBlank
        @Pattern(regexp = "^(es|es-VE|en)$", message = "{validation.locale.allowed}")
        String locale
) {}
