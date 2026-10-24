package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.push.ResultadoDemoPush;

/**
 * Resultado de una crecida simulada. Incluye la clasificación simulada y el
 * resumen del envío push a los seguidores del río. No representa un evento
 * real ni queda persistido.
 */
public record ResultadoSimulacionDTO(
        AlertaRioDTO alerta,
        int dispositivos,
        int enviados,
        int fallidos
) {

    public static ResultadoSimulacionDTO desde(AlertaRioDTO alerta, ResultadoDemoPush push) {
        return new ResultadoSimulacionDTO(
                alerta,
                push.dispositivos(),
                push.enviados(),
                push.fallidos()
        );
    }
}
