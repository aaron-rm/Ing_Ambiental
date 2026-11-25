package com.alerta.sistema_alerta_inundaciones.clima;

import java.time.Instant;
import java.util.List;

/**
 * Datos de precipitación listos para evaluar. Si {@code disponible} es false,
 * el resto de valores no deben interpretarse como mediciones reales.
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

    public record PuntoLluvia(Instant fecha, double mm) {
    }

    public static DatosPrecipitacion noDisponible(double latitud, double longitud, String motivo) {
        return new DatosPrecipitacion(
                false, latitud, longitud,
                "Open-Meteo (no disponible)", null,
                0.0, 0.0, 0.0, List.of(), motivo
        );
    }
}
