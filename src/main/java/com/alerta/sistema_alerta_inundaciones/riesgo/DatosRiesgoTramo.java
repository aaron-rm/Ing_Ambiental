package com.alerta.sistema_alerta_inundaciones.riesgo;

import com.alerta.sistema_alerta_inundaciones.clima.DatosPrecipitacion;

import java.time.Instant;
import java.util.List;

/**
 * Instantánea de los datos disponibles para evaluar un tramo. No contiene
 * decisiones de negocio: el nivel se calcula en {@link EvaluadorRiesgo}.
 *
 * @param reachId          identificador del tramo en GEOGloWS
 * @param nombreRio        nombre del río o tramo
 * @param latitud          latitud del río, o {@code null} si no está configurada
 * @param longitud         longitud del río, o {@code null} si no está configurada
 * @param caudalActual     caudal más cercano al instante actual, en m³/s
 * @param caudalMaximo     caudal máximo pronosticado, en m³/s
 * @param umbralVigilancia umbral de caudal de vigilancia, en m³/s
 * @param umbralAlerta     umbral de caudal de alerta, en m³/s
 * @param umbralEmergencia umbral de caudal de emergencia, en m³/s
 * @param fechaEmisionCaudal fecha de emisión del pronóstico de caudal
 * @param fuenteCaudal     descripción de la fuente del caudal
 * @param fuenteUmbrales   descripción de la fuente de los umbrales
 * @param pronostico       serie de caudal pronosticado
 * @param precipitacion    datos de precipitación asociados al tramo
 * @param advertencias     advertencias detectadas al reunir los datos
 */
public record DatosRiesgoTramo(
        long reachId,
        String nombreRio,
        Double latitud,
        Double longitud,
        double caudalActual,
        double caudalMaximo,
        double umbralVigilancia,
        double umbralAlerta,
        double umbralEmergencia,
        Instant fechaEmisionCaudal,
        String fuenteCaudal,
        String fuenteUmbrales,
        List<PuntoCaudal> pronostico,
        DatosPrecipitacion precipitacion,
        List<String> advertencias
) {

    /**
     * Valor de caudal pronosticado en un instante.
     *
     * @param fecha  instante del pronóstico
     * @param caudal caudal en m³/s
     */
    public record PuntoCaudal(Instant fecha, double caudal) {
    }
}
