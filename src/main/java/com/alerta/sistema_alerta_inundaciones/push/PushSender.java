package com.alerta.sistema_alerta_inundaciones.push;

public interface PushSender {

    boolean estaHabilitado();

    /** Motivo legible cuando FCM no está configurado; null si está habilitado. */
    String motivoDeshabilitado();

    ResultadoEnvioPush enviar(NotificacionPush notificacion, String token);
}
