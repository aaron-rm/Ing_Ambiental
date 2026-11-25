package com.alerta.sistema_alerta_inundaciones.push;

/**
 * Configuración pública necesaria para inicializar Firebase en la PWA.
 *
 * @param habilitado {@code true} si el push está operativo en servidor y cliente
 * @param motivo explicación cuando {@code habilitado} es {@code false}; {@code null} si está activo
 * @param apiKey API key pública de la app web de Firebase
 * @param authDomain dominio de autenticación de Firebase
 * @param projectId identificador del proyecto de Firebase
 * @param messagingSenderId identificador del remitente de mensajería
 * @param appId identificador de la app web de Firebase
 * @param vapidKey clave VAPID pública para suscripciones web push
 */
public record ConfigPushDTO(
        boolean habilitado,
        String motivo,
        String apiKey,
        String authDomain,
        String projectId,
        String messagingSenderId,
        String appId,
        String vapidKey
) {
}
