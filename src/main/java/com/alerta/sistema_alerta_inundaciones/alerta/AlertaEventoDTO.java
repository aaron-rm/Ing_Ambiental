package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.riesgo.EstadoDatos;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/** Representación de un evento de alerta para el historial y el detalle de la PWA. */
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
