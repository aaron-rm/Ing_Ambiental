package com.alerta.sistema_alerta_inundaciones.push;

/**
 * Resumen del envío de una alerta de demostración a todos los dispositivos
 * activos. No persiste un evento de riesgo: es solo para mostrar el canal push.
 */
public record ResultadoDemoPush(int dispositivos, int enviados, int fallidos) {
}
