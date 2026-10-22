package com.alerta.sistema_alerta_inundaciones.push;

import java.time.Instant;

/** Respuesta de registro sin exponer el token ni datos de otros dispositivos. */
public record RegistroPushResponse(
        Long id,
        Long reachId,
        boolean activo,
        String mensaje,
        Instant fechaActualizacion
) {
}
