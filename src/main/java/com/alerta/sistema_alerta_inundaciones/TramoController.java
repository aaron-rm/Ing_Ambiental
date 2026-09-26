package com.alerta.sistema_alerta_inundaciones;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.*;

import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =====================================================================================
 *  CONTROLADOR REST
 * =====================================================================================
 *
 * Expone por HTTP la lógica de LectorDatosIngresados / ClienteApiGeoglows /
 * GeneradorAlertaFinal, con las mismas rutas que espera el HTML del front:
 *
 *   GET  /api/tramos/{reachId}/reporte
 *   POST /api/tramos/{reachId}/sincronizar?cuencaId=...
 *   POST /api/tramos/{reachId}/observaciones
 *
 * Guarda los repositorios en memoria (un LectorDatosIngresados por reachId).
 * Para producción, reemplazar el Map en memoria por persistencia real (JPA, etc.)
 * y mover CORS a una configuración global en vez de @CrossOrigin aquí.
 * =====================================================================================
 */
@RestController
@RequestMapping("/api/tramos")
@CrossOrigin(origins = "*") // ajustar a tu dominio real en producción
public class TramoController {

    // Un repositorio por tramo, en memoria. Sustituir por una base de datos si se necesita persistencia.
    private final Map<Long, LectorDatosIngresados> repos = new ConcurrentHashMap<>();
    private final GeneradorAlertaFinal generador = new GeneradorAlertaFinal();

    private LectorDatosIngresados repoDe(long reachId) {
        return repos.computeIfAbsent(reachId, id -> new LectorDatosIngresados());
    }

    /** POST /api/tramos/{reachId}/sincronizar?cuencaId=... */
    @PostMapping("/{reachId}/sincronizar")
    public Map<String, Object> sincronizar(
            @PathVariable long reachId,
            @RequestParam(required = false, defaultValue = "N/D") String cuencaId) throws Exception {
        LectorDatosIngresados repo = repoDe(reachId);
        repo.sincronizarTramo(reachId, cuencaId, "N/D");
        return Map.of("estado", "sincronizado", "reachId", reachId);
    }

    /** GET /api/tramos/{reachId}/reporte */
    @GetMapping("/{reachId}/reporte")
    public Map<String, Object> reporte(@PathVariable long reachId) {
        LectorDatosIngresados repo = repos.get(reachId);
        LectorDatosIngresados.PronosticoCaudal p = (repo != null) ? repo.pronosticos.get(reachId) : null;

        if (repo == null || p == null) {
            // El front interpreta un 404 real como "falta sincronizar"; para eso lanzamos
            // una respuesta 404 explícita en vez de devolver un mapa vacío.
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Tramo " + reachId + " no sincronizado todavía");
        }

        LectorDatosIngresados.UmbralAlerta u = repo.umbrales.get(reachId);
        LectorDatosIngresados.NivelAlerta nivel = generador.evaluarNivel(repo, reachId);
        double caudalMaximoPrevisto = p.mediaSerie.stream()
                .mapToDouble(pt -> pt.valor)
                .max()
                .orElse(0.0);

        Map<String, Object> umbralJson = new LinkedHashMap<>();
        if (u != null) {
            umbralJson.put("caudalVigilancia", u.caudalVigilancia);
            umbralJson.put("caudalAlerta", u.caudalAlerta);
            umbralJson.put("caudalEmergencia", u.caudalEmergencia);
            umbralJson.put("fuenteCalibracion", u.fuenteCalibracion);
            umbralJson.put("fechaCalibracion", u.fechaCalibracion != null ? u.fechaCalibracion.toString() : null);
        }

        List<Map<String, Object>> observacionesJson = new ArrayList<>();
        for (LectorDatosIngresados.ObservacionTiempoReal o : repo.observaciones) {
            if (o.reachIdAsociado != null && o.reachIdAsociado == reachId) {
                Map<String, Object> oj = new LinkedHashMap<>();
                oj.put("estacionId", o.estacionId);
                oj.put("tipo", o.tipo != null ? o.tipo.name() : null);
                oj.put("valor", o.valor);
                oj.put("unidad", o.unidad);
                oj.put("fecha", o.fecha != null ? o.fecha.toString() : null);
                observacionesJson.add(oj);
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("reachId", reachId);
        resultado.put("nivelAlerta", nivel.name());
        resultado.put("caudalMaximoPrevisto", caudalMaximoPrevisto);
        resultado.put("fechaEmision", p.fechaEmision != null ? p.fechaEmision.toString() : null);
        resultado.put("horizonteHoras", p.horizonteHoras);
        resultado.put("caudalHistoricoPromedio", p.caudalHistoricoPromedio);
        resultado.put("umbral", umbralJson);
        resultado.put("observaciones", observacionesJson);
        return resultado;
    }

    /** POST /api/tramos/{reachId}/observaciones */
    @PostMapping("/{reachId}/observaciones")
    public Map<String, Object> agregarObservacion(
            @PathVariable long reachId,
            @RequestBody Map<String, Object> body) {
        LectorDatosIngresados repo = repoDe(reachId);

        LectorDatosIngresados.ObservacionTiempoReal obs = new LectorDatosIngresados.ObservacionTiempoReal();
        obs.estacionId = String.valueOf(body.get("estacionId"));
        obs.tipo = LectorDatosIngresados.TipoObservacion.valueOf(String.valueOf(body.get("tipo")));
        obs.valor = ((Number) body.get("valor")).doubleValue();
        obs.unidad = body.get("unidad") != null ? String.valueOf(body.get("unidad")) : null;
        obs.fecha = Instant.now();
        obs.reachIdAsociado = reachId;

        repo.ingresarObservacion(obs);
        return Map.of("estado", "guardado");
    }
}
