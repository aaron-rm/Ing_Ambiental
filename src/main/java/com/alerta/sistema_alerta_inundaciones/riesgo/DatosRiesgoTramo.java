package com.alerta.sistema_alerta_inundaciones.riesgo;

import com.alerta.sistema_alerta_inundaciones.clima.DatosPrecipitacion;

import java.time.Instant;
import java.util.List;

/**
 * Instantánea de los datos disponibles para evaluar un tramo. No contiene
 * decisiones de negocio: el nivel se calcula en {@link EvaluadorRiesgo}.
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

    public record PuntoCaudal(Instant fecha, double caudal) {
    }
}
