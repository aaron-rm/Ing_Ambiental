package com.alerta.sistema_alerta_inundaciones.rio;

/**
 * Cuerpo de solicitud para registrar una muestra de laboratorio o campo asociada a un río.
 *
 * @param fecha     fecha y hora de la toma de muestra en formato ISO o fecha local
 * @param parametro nombre del parámetro medido (por ejemplo, pH, turbidez)
 * @param valor     valor numérico registrado
 * @param unidad    unidad de medida del parámetro (opcional)
 * @param nota      observación adicional sobre la muestra (opcional)
 */
public record CrearMuestraRequest(
        String fecha,
        String parametro,
        Double valor,
        String unidad,
        String nota
) {
}
