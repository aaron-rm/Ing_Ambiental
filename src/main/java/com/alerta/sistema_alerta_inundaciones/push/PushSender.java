package com.alerta.sistema_alerta_inundaciones.push;

/**
 * Contrato para enviar notificaciones push a un dispositivo. Desacopla el
 * servicio de negocio del proveedor concreto (Firebase Cloud Messaging) y
 * facilita sustituirlo en pruebas.
 */
public interface PushSender {

    /**
     * Indica si el proveedor de push está configurado y listo para enviar.
     *
     * @return {@code true} si se pueden enviar notificaciones
     */
    boolean estaHabilitado();

    /**
     * Motivo legible cuando FCM no está configurado; null si está habilitado.
     *
     * @return motivo de la deshabilitación, o {@code null} si está habilitado
     */
    String motivoDeshabilitado();

    /**
     * Envía una notificación a un dispositivo identificado por su token.
     *
     * @param notificacion contenido de la notificación
     * @param token token de registro del dispositivo destino
     * @return resultado del envío (éxito, token inválido o fallo)
     */
    ResultadoEnvioPush enviar(NotificacionPush notificacion, String token);
}
