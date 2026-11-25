package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.riesgo.EstadoDatos;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Representación de un evento de alerta para el historial y el detalle de la PWA.
 * Expone los datos de {@link AlertaEvento} con los textos multilínea convertidos en listas.
 *
 * @param id                  identificador del evento
 * @param reachId             identificador del tramo en GEOGloWS
 * @param nombreRio           nombre del río
 * @param nivel               nivel final de alerta
 * @param nivelAnterior       nivel previo al cambio
 * @param nivelCaudal         nivel calculado solo con el caudal
 * @param nivelLluvia         nivel calculado solo con la lluvia
 * @param estadoDatos         calidad de los datos usados
 * @param datosConfiables     si la evaluación se consideró confiable
 * @param caudalMaximo        caudal máximo pronosticado en m³/s
 * @param umbralVigilancia    umbral de vigilancia en m³/s
 * @param umbralAlerta        umbral de alerta en m³/s
 * @param umbralEmergencia    umbral de emergencia en m³/s
 * @param lluvia24hMm         lluvia prevista en 24 h, en mm
 * @param lluvia72hMm         lluvia prevista en 72 h, en mm
 * @param mensaje             mensaje de la alerta
 * @param factores            factores que explican el nivel
 * @param advertencias        advertencias sobre los datos
 * @param fuenteCaudal        fuente de los datos de caudal
 * @param fuentePrecipitacion fuente de los datos de precipitación
 * @param fechaDatos          fecha de emisión del pronóstico de caudal
 * @param fechaEvaluacion     fecha en que se evaluó el riesgo
 * @param fechaCreacion       fecha de creación del evento
 * @param estadoEnvio         estado del envío push
 * @param fechaIntentoEnvio   fecha del último intento de envío
 * @param fechaEnvio          fecha del envío exitoso
 * @param dispositivosObjetivo dispositivos a los que se intentó notificar
 * @param dispositivosEnviados dispositivos notificados correctamente
 * @param dispositivosFallidos dispositivos cuyo envío falló
 * @param errorEnvio          descripción del error de envío, si lo hubo
 * @param origen              origen de la evaluación (por ejemplo PROGRAMADO o MANUAL)
 */
public record AlertaEventoDTO(
        Long id,
        Long reachId,
        String nombreRio,
        NivelAlerta nivel,
        NivelAlerta nivelAnterior,
        NivelAlerta nivelCaudal,
        NivelAlerta nivelLluvia,
        EstadoDatos estadoDatos,
        boolean datosConfiables,
        double caudalMaximo,
        double umbralVigilancia,
        double umbralAlerta,
        double umbralEmergencia,
        Double lluvia24hMm,
        Double lluvia72hMm,
        String mensaje,
        List<String> factores,
        List<String> advertencias,
        String fuenteCaudal,
        String fuentePrecipitacion,
        Instant fechaDatos,
        Instant fechaEvaluacion,
        Instant fechaCreacion,
        EstadoEnvio estadoEnvio,
        Instant fechaIntentoEnvio,
        Instant fechaEnvio,
        int dispositivosObjetivo,
        int dispositivosEnviados,
        int dispositivosFallidos,
        String errorEnvio,
        String origen
) {

    /**
     * Convierte una entidad {@link AlertaEvento} en su representación de transferencia.
     *
     * @param evento entidad persistida del evento de alerta
     * @return DTO equivalente con listas de factores y advertencias separadas por línea
     */
    public static AlertaEventoDTO desde(AlertaEvento evento) {
        return new AlertaEventoDTO(
                evento.getId(),
                evento.getReachId(),
                evento.getNombreRio(),
                evento.getNivel(),
                evento.getNivelAnterior(),
                evento.getNivelCaudal(),
                evento.getNivelLluvia(),
                evento.getEstadoDatos(),
                evento.isDatosConfiables(),
                evento.getCaudalMaximo(),
                evento.getUmbralVigilancia(),
                evento.getUmbralAlerta(),
                evento.getUmbralEmergencia(),
                evento.getLluvia24hMm(),
                evento.getLluvia72hMm(),
                evento.getMensaje(),
                separar(evento.getFactores()),
                separar(evento.getAdvertencias()),
                evento.getFuenteCaudal(),
                evento.getFuentePrecipitacion(),
                evento.getFechaDatos(),
                evento.getFechaEvaluacion(),
                evento.getFechaCreacion(),
                evento.getEstadoEnvio(),
                evento.getFechaIntentoEnvio(),
                evento.getFechaEnvio(),
                evento.getDispositivosObjetivo(),
                evento.getDispositivosEnviados(),
                evento.getDispositivosFallidos(),
                evento.getErrorEnvio(),
                evento.getOrigen()
        );
    }

    // Divide bloques multilínea persistidos en una lista limpia de elementos no vacíos.
    private static List<String> separar(String texto) {
        if (texto == null || texto.isBlank()) {
            return List.of();
        }
        return Arrays.stream(texto.split("\\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
