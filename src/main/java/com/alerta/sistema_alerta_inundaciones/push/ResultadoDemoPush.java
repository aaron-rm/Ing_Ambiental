package com.alerta.sistema_alerta_inundaciones.push;

/**
 * Resumen del envío de una alerta de demostración a todos los dispositivos
 * activos. No persiste un evento de riesgo: es solo para mostrar el canal push.
 *
 * @param dispositivos cantidad de dispositivos objetivo
 * @param enviados cantidad de notificaciones entregadas a FCM
 * @param fallidos cantidad de envíos que fallaron
 */
public record ResultadoDemoPush(int dispositivos, int enviados, int fallidos) {
}
