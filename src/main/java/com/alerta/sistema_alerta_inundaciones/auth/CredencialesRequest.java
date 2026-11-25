package com.alerta.sistema_alerta_inundaciones.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CredencialesRequest(
        @NotBlank(message = "El identificador es obligatorio.")
        @Size(min = 3, max = 80, message = "El identificador debe tener entre 3 y 80 caracteres.")
        String identificador,

        @NotBlank(message = "La contraseña es obligatoria.")
        @Size(min = 8, max = 72, message = "La contraseña debe tener entre 8 y 72 caracteres.")
        String password
) {
}
