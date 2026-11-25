package com.alerta.sistema_alerta_inundaciones.riesgo;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados;

import java.time.Instant;
import java.util.List;

/**
 * Resultado completo de una evaluación. Incluye el nivel final, los niveles
 * parciales y las advertencias para que la interfaz no presente una certeza
 * que los datos no tienen.
 *
 * @param reachId                 identificador del tramo en GEOGloWS
 * @param nombreRio               nombre del río o tramo
 * @param nivel                   nivel final, el mayor entre caudal y lluvia
 * @param nivelCaudal             nivel calculado con el caudal máximo pronosticado
 * @param nivelLluvia             nivel calculado con la lluvia prevista en 24 h
 * @param confiable               si el pronóstico de caudal es reciente y la evaluación puede notificar
 * @param estadoDatos             calidad de los datos usados
 * @param caudalActual            caudal más cercano al instante actual, en m³/s
 * @param caudalMaximo            caudal máximo pronosticado, en m³/s
 * @param umbralVigilancia        umbral de caudal de vigilancia, en m³/s
 * @param umbralAlerta            umbral de caudal de alerta, en m³/s
 * @param umbralEmergencia        umbral de caudal de emergencia, en m³/s
 * @param lluvia24hMm             lluvia prevista en 24 h, en mm; {@code null} si no hay datos
 * @param lluvia72hMm             lluvia prevista en 72 h, en mm; {@code null} si no hay datos
 * @param fechaEvaluacion         instante en que se hizo la evaluación
 * @param fechaEmisionCaudal      fecha de emisión del pronóstico de caudal
 * @param fechaDatosPrecipitacion fecha de consulta de la precipitación; {@code null} si no hay datos
 * @param fuenteCaudal            fuente de los datos de caudal
 * @param fuentePrecipitacion     fuente de los datos de precipitación
 * @param fuenteUmbrales          fuente de los umbrales de caudal
 * @param factores                factores que explican el nivel
 * @param advertencias            advertencias sobre la calidad de los datos
 * @param mensaje                 mensaje descriptivo del resultado
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
