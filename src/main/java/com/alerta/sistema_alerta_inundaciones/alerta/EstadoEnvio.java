package com.alerta.sistema_alerta_inundaciones.alerta;

/**
 * Estado del envío de notificaciones push asociado a un evento de alerta.
 * Permite saber si el evento ya fue notificado y evita reenviarlo tras un
 * reinicio del backend.
 */
public enum EstadoEnvio {
    /** El evento fue creado y aún no se ha intentado enviar. */
    PENDIENTE,
    /** El envío está en curso. */
    ENVIANDO,
    /** Todas las notificaciones se enviaron correctamente. */
    ENVIADA,
    /** Algunas notificaciones se enviaron y otras fallaron. */
    PARCIAL,
    /** Ninguna notificación pudo enviarse. */
    FALLIDA,
    /** No había dispositivos registrados para los seguidores del río. */
    SIN_DISPOSITIVOS,
    /** El envío push está deshabilitado por configuración. */
    DESHABILITADO,
    /** El evento no requiere notificación según su nivel. */
    OMITIDA
}
