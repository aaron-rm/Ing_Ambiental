package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.push.ResultadoDemoPush;

/**
 * Resultado de una crecida simulada. Incluye la clasificación simulada y el
 * resumen del envío push a los seguidores del río. No representa un evento
 * real ni queda persistido.
 *
 * @param alerta detalle simulado del estado de alerta del río
 * @param dispositivos cantidad de dispositivos objetivo de los seguidores del río
 * @param enviados cantidad de notificaciones push entregadas con éxito
 * @param fallidos cantidad de notificaciones push que fallaron durante el envío
 */
public record ResultadoSimulacionDTO(
        AlertaRioDTO alerta,
        int dispositivos,
        int enviados,
        int fallidos
) {

    /**
     * Construye el DTO de simulación combinando la alerta simulada y el resumen del envío push.
     *
     * @param alerta detalle de alerta generado para la simulación
     * @param push resultado del envío de notificaciones push de prueba
     * @return instancia consolidada con los datos de simulación y entrega
     */
    public static ResultadoSimulacionDTO desde(AlertaRioDTO alerta, ResultadoDemoPush push) {
        return new ResultadoSimulacionDTO(
                alerta,
                push.dispositivos(),
                push.enviados(),
                push.fallidos()
        );
    }
}
