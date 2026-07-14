package com.fenixcore.optibienestar360.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record RecoverPasswordRequest(
        @NotBlank @Email String email
) {}
