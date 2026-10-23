package com.alerta.sistema_alerta_inundaciones.push;

public record RegistroPushResponse(
        Long id,
        boolean activo,
        String mensaje,
        java.time.Instant fechaActualizacion
) {
}
