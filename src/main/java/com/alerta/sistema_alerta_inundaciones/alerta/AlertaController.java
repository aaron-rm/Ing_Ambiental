package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.config.PropiedadesRiesgo;
import com.alerta.sistema_alerta_inundaciones.push.PushService;
import com.alerta.sistema_alerta_inundaciones.push.ResultadoDemoPush;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final PushService pushService;
    private final PropiedadesRiesgo propiedadesRiesgo;
    private final String adminToken;

    public AlertaController(AlertaService alertaService,
                            AlertaMonitoreoService monitoreoService,
                            PushService pushService,
                            PropiedadesRiesgo propiedadesRiesgo,
                            @Value("${alerta.admin.token:}") String adminToken) {
        this.alertaService = alertaService;
        this.monitoreoService = monitoreoService;
        this.pushService = pushService;
        this.propiedadesRiesgo = propiedadesRiesgo;
        this.adminToken = adminToken;
    }

    /** Consulta el estado de alerta y pronóstico de un tramo directamente por reachId de GEOGloWS. */
    @GetMapping("/tramo/{reachId}")
    public ResponseEntity<?> consultarAlertaPorTramo(@PathVariable Long reachId,
                                                     @AuthenticationPrincipal Usuario usuario) {
        try {
            AlertaRioDTO alerta = alertaService.consultarAlertaTramo(
                    reachId, "Tramo GEOGLOWS " + reachId, usuario);
            return ResponseEntity.ok(alerta);
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(502).body("Error al consultar GEOGLOWS: " + e.getMessage());
        }
    }

    /** Consulta el estado de alerta a partir del ID de un Río persistido en la BD. */
    @GetMapping("/rio/{rioId}")
    public ResponseEntity<?> consultarAlertaPorRio(@PathVariable Long rioId,
                                                   @AuthenticationPrincipal Usuario usuario) {
        try {
            return ResponseEntity.ok(alertaService.consultarAlertaPorRioId(rioId, usuario));
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
     * Simulación de crecida. No persiste un evento, pero envía un push de
     * simulacro a los dispositivos de los usuarios que siguen el río. Si no se
     * indica caudal, se usa uno superior al umbral de emergencia del tramo.
     */
    @PostMapping("/simular")
    public ResponseEntity<?> simularInundacion(@RequestParam Long reachId,
                                               @RequestParam(required = false) Double caudal) {
        try {
            AlertaRioDTO alerta = alertaService.simularInundacion(reachId, caudal);
            ResultadoDemoPush push = pushService.enviarSimulacion(alerta);
            return ResponseEntity.ok(ResultadoSimulacionDTO.desde(alerta, push));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(502).body("Error al ejecutar simulación: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(502).body("No se pudo enviar la simulación: " + e.getMessage());
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
