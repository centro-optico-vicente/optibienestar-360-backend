package com.fenixcore.optisaludplus.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record AdminCreateUserRequest(
        @NotBlank @Email String email,
        @NotBlank String fullName,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Pattern(regexp = "^[VE]$", message = "{validation.document_type.format}") String documentType,
        String documentNumber,
        String phone,
        @NotEmpty List<UUID> roleIds
) {}
