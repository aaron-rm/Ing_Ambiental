package com.alerta.sistema_alerta_inundaciones.clima;

import java.time.Instant;
import java.util.List;

/**
 * Datos de precipitación listos para evaluar. Si {@code disponible} es false,
 * el resto de valores no deben interpretarse como mediciones reales.
 *
 * @param disponible         si la consulta devolvió datos utilizables
 * @param latitud            latitud consultada, en grados
 * @param longitud           longitud consultada, en grados
 * @param fuente             descripción de la fuente de los datos
 * @param fechaConsulta      instante en que se consultó la fuente; {@code null} si no hubo consulta válida
 * @param lluvia24hMm        lluvia acumulada prevista en las próximas 24 h, en mm
 * @param lluvia72hMm        lluvia acumulada prevista en las próximas 72 h, en mm
 * @param maxLluviaHorariaMm máxima lluvia horaria prevista en las próximas 72 h, en mm
 * @param serie              serie horaria de precipitación
 * @param advertencia        aviso sobre la calidad o ausencia de los datos; {@code null} si no hay
 */
public record DatosPrecipitacion(
        boolean disponible,
        double latitud,
        double longitud,
        String fuente,
        Instant fechaConsulta,
        double lluvia24hMm,
        double lluvia72hMm,
        double maxLluviaHorariaMm,
        List<PuntoLluvia> serie,
        String advertencia
) {

    /**
     * Valor de precipitación de una hora concreta.
     *
     * @param fecha instante de la hora, en UTC
     * @param mm    precipitación en milímetros
     */
    public record PuntoLluvia(Instant fecha, double mm) {
    }

    /**
     * Crea un resultado que indica que no hay datos de precipitación.
     *
     * @param latitud  latitud consultada, en grados
     * @param longitud longitud consultada, en grados
     * @param motivo   explicación de por qué no hay datos; se guarda como advertencia
     * @return instancia con {@code disponible} en false y valores en cero
     */
    public static DatosPrecipitacion noDisponible(double latitud, double longitud, String motivo) {
        return new DatosPrecipitacion(
                false, latitud, longitud,
                "Open-Meteo (no disponible)", null,
                0.0, 0.0, 0.0, List.of(), motivo
        );
    }
}
