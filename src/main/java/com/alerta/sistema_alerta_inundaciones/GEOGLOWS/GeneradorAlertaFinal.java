package com.alerta.sistema_alerta_inundaciones.GEOGLOWS;

import java.time.Instant;

/**
 * =====================================================================================
 *  GENERADOR DE ALERTA FINAL
 * =====================================================================================
 *
 * Responsabilidad: tomar los datos ya leídos/sincronizados en {@link LectorDatosIngresados}
 * y ENTREGAR el resultado final: el nivel de alerta del tramo y un reporte legible con
 * los umbrales de referencia.
 *
 * Esta es la única clase que decide/expone el resultado de negocio; no habla con la API
 * ni conoce detalles de parseo de JSON.
 * =====================================================================================
 */
public class GeneradorAlertaFinal {

    /** Compara el máximo previsto contra los umbrales para decidir el nivel de alerta del tramo. */
    public LectorDatosIngresados.NivelAlerta evaluarNivel(LectorDatosIngresados repo, long reachId) {
        LectorDatosIngresados.PronosticoCaudal p = repo.pronosticos.get(reachId);
        LectorDatosIngresados.UmbralAlerta u = repo.umbrales.get(reachId);
        if (p == null || u == null || p.mediaSerie.isEmpty()) {
            return LectorDatosIngresados.NivelAlerta.NORMAL;
        }

        double maxPrevisto = p.mediaSerie.stream().mapToDouble(pt -> pt.valor).max().orElse(0.0);
        if (maxPrevisto >= u.caudalEmergencia) return LectorDatosIngresados.NivelAlerta.EMERGENCIA;
        if (maxPrevisto >= u.caudalAlerta) return LectorDatosIngresados.NivelAlerta.ALERTA;
        if (maxPrevisto >= u.caudalVigilancia) return LectorDatosIngresados.NivelAlerta.VIGILANCIA;
        return LectorDatosIngresados.NivelAlerta.NORMAL;
    }

    /** Arma y muestra el reporte final para un tramo ya sincronizado. */
    public void generarReporte(LectorDatosIngresados repo, long reachId) {
        LectorDatosIngresados.UmbralAlerta u = repo.umbrales.get(reachId);
        LectorDatosIngresados.NivelAlerta nivel = evaluarNivel(repo, reachId);

        System.out.println("Tramo " + reachId + " -> nivel actual: " + nivel);
        if (u != null) {
            System.out.printf("Umbrales (m3/s) -> vigilancia=%.1f alerta=%.1f emergencia=%.1f%n",
                    u.caudalVigilancia, u.caudalAlerta, u.caudalEmergencia);
        }
        System.out.println("Observaciones locales registradas: " + repo.observaciones.size());
    }

    // =================================================================================
    // DEMO
    // =================================================================================

    public static void main(String[] args) {
        LectorDatosIngresados repo = new LectorDatosIngresados();
        GeneradorAlertaFinal generador = new GeneradorAlertaFinal();

        // Reemplazar por el reach_id/LINKNO real del tramo que se quiere monitorear.
        long reachId = 710039498L;

        try {
            repo.sincronizarTramo(reachId, "cuenca-demo", "N/D");
        } catch (Exception e) {
            System.out.println("No se pudo consultar GEOGloWS ahora mismo: " + e.getMessage());
        }

        // Ejemplo de dato que GEOGloWS NO provee y debe cargarse desde una fuente propia:
        LectorDatosIngresados.ObservacionTiempoReal obs = new LectorDatosIngresados.ObservacionTiempoReal();
        obs.estacionId = "EST-001";
        obs.tipo = LectorDatosIngresados.TipoObservacion.NIVEL;
        obs.valor = 2.35;
        obs.unidad = "m";
        obs.fecha = Instant.now();
        obs.reachIdAsociado = reachId;
        repo.ingresarObservacion(obs);

        // Entrega del resultado final
        generador.generarReporte(repo, reachId);
    }
}
