package com.alerta.sistema_alerta_inundaciones.riesgo;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados;

import java.time.Instant;
import java.util.List;

/**
 * Resultado completo de una evaluación. Incluye el nivel final, los niveles
 * parciales y las advertencias para que la interfaz no presente una certeza
 * que los datos no tienen.
 */
public record EvaluacionRiesgo(
        long reachId,
        String nombreRio,
        LectorDatosIngresados.NivelAlerta nivel,
        LectorDatosIngresados.NivelAlerta nivelCaudal,
        LectorDatosIngresados.NivelAlerta nivelLluvia,
        boolean confiable,
        EstadoDatos estadoDatos,
        double caudalActual,
        double caudalMaximo,
        double umbralVigilancia,
        double umbralAlerta,
        double umbralEmergencia,
        Double lluvia24hMm,
        Double lluvia72hMm,
        Instant fechaEvaluacion,
        Instant fechaEmisionCaudal,
        Instant fechaDatosPrecipitacion,
        String fuenteCaudal,
        String fuentePrecipitacion,
        String fuenteUmbrales,
        List<String> factores,
        List<String> advertencias,
        String mensaje
) {
}
