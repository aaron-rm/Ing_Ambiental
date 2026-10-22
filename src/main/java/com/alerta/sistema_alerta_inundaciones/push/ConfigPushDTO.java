package com.alerta.sistema_alerta_inundaciones.push;

/** Configuración pública necesaria para inicializar Firebase en la PWA. */
public record ConfigPushDTO(
        boolean habilitado,
        String motivo,
        String apiKey,
        String authDomain,
        String projectId,
        String messagingSenderId,
        String appId,
        String vapidKey,
        Long reachId
) {
}
