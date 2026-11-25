package com.alerta.sistema_alerta_inundaciones.push;

/**
 * Respuesta al registrar un dispositivo para push.
 *
 * @param id identificador del dispositivo registrado
 * @param activo {@code true} si la suscripción quedó activa
 * @param mensaje mensaje descriptivo para el cliente
 * @param fechaActualizacion fecha de la última actualización del registro
 */
public record RegistroPushResponse(
        Long id,
        boolean activo,
        String mensaje,
        java.time.Instant fechaActualizacion
) {
}
