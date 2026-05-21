package com.fenixcore.optisaludplus.modules.contact.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContactRequestDto(

        @NotBlank
        @Size(max = 150)
        String name,

        @NotBlank
        @Email
        @Size(max = 254)
        String email,

        @Size(max = 30)
        String phone,

        @NotBlank
        @Size(max = 200)
        String subject,

        @NotBlank
        @Size(max = 2000)
        String message
) {
}
