package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.config.PropiedadesRiesgo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaService alertaService;
    private final AlertaMonitoreoService monitoreoService;
    private final PropiedadesRiesgo propiedadesRiesgo;
    private final String adminToken;

    public AlertaController(AlertaService alertaService,
                            AlertaMonitoreoService monitoreoService,
                            PropiedadesRiesgo propiedadesRiesgo,
                            @Value("${alerta.admin.token:}") String adminToken) {
        this.alertaService = alertaService;
        this.monitoreoService = monitoreoService;
        this.propiedadesRiesgo = propiedadesRiesgo;
        this.adminToken = adminToken;
    }

    /** Consulta el estado de alerta y pronóstico de un tramo directamente por reachId de GEOGloWS. */
    @GetMapping("/tramo/{reachId}")
    public ResponseEntity<?> consultarAlertaPorTramo(@PathVariable Long reachId) {
        try {
            AlertaRioDTO alerta = alertaService.consultarAlertaTramo(reachId, "Tramo GEOGLOWS " + reachId);
            return ResponseEntity.ok(alerta);
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(502).body("Error al consultar GEOGLOWS: " + e.getMessage());
        }
    }

    /** Consulta el estado de alerta a partir del ID de un Río persistido en la BD. */
    @GetMapping("/rio/{rioId}")
    public ResponseEntity<?> consultarAlertaPorRio(@PathVariable Long rioId) {
        try {
            return ResponseEntity.ok(alertaService.consultarAlertaPorRioId(rioId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(502).body("Error al consultar GEOGLOWS: " + e.getMessage());
        }
    }

    /** Historial de eventos de un tramo (no incluye datos personales). */
    @GetMapping("/historial/{reachId}")
    public ResponseEntity<List<AlertaEventoDTO>> historial(
            @PathVariable Long reachId,
            @RequestParam(defaultValue = "20") int limite) {
        return ResponseEntity.ok(alertaService.obtenerHistorial(reachId, limite));
    }

    /** Detalle de una alerta concreta; es el destino del clic de la notificación. */
    @GetMapping("/evento/{eventoId}")
    public ResponseEntity<?> evento(@PathVariable Long eventoId) {
        try {
            return ResponseEntity.ok(alertaService.obtenerEvento(eventoId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /** Umbrales y avisos para que la interfaz explique cómo se calcula el nivel. */
    @GetMapping("/config-riesgo")
    public ResponseEntity<Map<String, Object>> configuracionRiesgo() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("umbralLluviaVigilanciaMm", propiedadesRiesgo.getLluviaUmbralVigilanciaMm());
        config.put("umbralLluviaAlertaMm", propiedadesRiesgo.getLluviaUmbralAlertaMm());
        config.put("umbralLluviaEmergenciaMm", propiedadesRiesgo.getLluviaUmbralEmergenciaMm());
        config.put("maxEdadPronosticoHoras", propiedadesRiesgo.getMaxEdadPronosticoHoras());
        config.put("maxEdadPrecipitacionHoras", propiedadesRiesgo.getMaxEdadPrecipitacionHoras());
        config.put("aviso", "Los umbrales de lluvia son provisionales/experimentales y no oficiales. "
                + "Los umbrales de caudal provienen de GEOGloWS o de valores de referencia si la API no responde. "
                + "Este sistema no emite declaraciones oficiales de emergencia.");
        return ResponseEntity.ok(config);
    }

    /**
     * Suscribe un contacto (correo o teléfono) para recibir avisos por los
     * canales heredados. No registra tokens push.
     */
    @PostMapping("/suscribir")
    public ResponseEntity<?> suscribir(@RequestBody SuscripcionAlerta req) {
        try {
            SuscripcionAlerta guardada = alertaService.suscribirUsuario(
                    req.getReachId(), req.getNombreContacto(), req.getContacto());
            return ResponseEntity.ok(guardada);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /** Lista suscripciones heredadas; contiene datos de contacto, por eso exige token administrativo. */
    @GetMapping("/suscripciones/{reachId}")
    public ResponseEntity<?> obtenerSuscripciones(@PathVariable Long reachId,
                                                  @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!adminAutorizado(token)) {
            return ResponseEntity.status(403).body(
                    "Acceso restringido. Configure ALERTA_ADMIN_TOKEN y envíelo en X-Admin-Token.");
        }
        return ResponseEntity.ok(alertaService.obtenerSuscripcionesPorTramo(reachId));
    }

    /**
     * Evalúa un tramo con datos reales y, si cambia el nivel, crea el evento y
     * envía las notificaciones push. Requiere JWT con rol ADMIN.
     */
    @PostMapping("/evaluar/{reachId}")
    public ResponseEntity<?> evaluar(@PathVariable Long reachId) {
        try {
            return ResponseEntity.ok(monitoreoService.evaluarPorReachId(reachId, "MANUAL"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(502).body("No se pudo evaluar el tramo: " + e.getMessage());
        }
    }

    /**
     * Simulación de caudal. No persiste eventos ni envía push reales; el
     * resultado se marca explícitamente como simulación.
     */
    @PostMapping("/simular")
    public ResponseEntity<?> simularInundacion(@RequestParam Long reachId, @RequestParam double caudal) {
        try {
            AlertaRioDTO resultado = alertaService.simularInundacion(reachId, caudal);
            return ResponseEntity.ok(resultado);
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(502).body("Error al ejecutar simulación: " + e.getMessage());
        }
    }

    private boolean adminAutorizado(String tokenRecibido) {
        if (adminToken == null || adminToken.isBlank()) {
            return false;
        }
        byte[] esperado = adminToken.getBytes(StandardCharsets.UTF_8);
        byte[] recibido = (tokenRecibido == null ? "" : tokenRecibido).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, recibido);
    }
}
