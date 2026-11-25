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

/**
 * Controlador REST que expone los endpoints de consulta de alertas por tramo o río,
 * historial de eventos, configuración de umbrales, suscripciones heredadas y
 * evaluación o simulación administrativa de crecidas.
 */
@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaService alertaService;
    private final AlertaMonitoreoService monitoreoService;
    private final PushService pushService;
    private final PropiedadesRiesgo propiedadesRiesgo;
    private final String adminToken;

    /**
     * Crea el controlador de alertas con los servicios y configuración requeridos.
     *
     * @param alertaService servicio de consulta, historial, suscripciones y simulación de alertas
     * @param monitoreoService servicio que ejecuta la evaluación y persistencia de eventos de riesgo
     * @param pushService servicio encargado del envío de notificaciones push
     * @param propiedadesRiesgo configuración de umbrales y vigencia de datos de riesgo
     * @param adminToken token administrativo heredado para proteger rutas de consulta restringida
     */
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

    /**
     * Consulta el estado de alerta y pronóstico de un tramo directamente por reachId de GEOGloWS.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @param usuario usuario autenticado opcional para indicar si sigue el río
     * @return respuesta HTTP con el estado de alerta del tramo o código 502 si falla la fuente externa
     */
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

    /**
     * Consulta el estado de alerta a partir del ID de un Río persistido en la BD.
     *
     * @param rioId identificador del río registrado en la base de datos
     * @param usuario usuario autenticado opcional para indicar si sigue el río
     * @return respuesta HTTP con el detalle de alerta del río, 404 si no existe o 502 si falla GEOGloWS
     */
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

    /**
     * Historial de eventos de un tramo (no incluye datos personales).
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @param limite cantidad máxima de eventos a devolver
     * @return respuesta HTTP con la lista de eventos de alerta más recientes
     */
    @GetMapping("/historial/{reachId}")
    public ResponseEntity<List<AlertaEventoDTO>> historial(
            @PathVariable Long reachId,
            @RequestParam(defaultValue = "20") int limite) {
        return ResponseEntity.ok(alertaService.obtenerHistorial(reachId, limite));
    }

    /**
     * Detalle de una alerta concreta; es el destino del clic de la notificación.
     *
     * @param eventoId identificador del evento de alerta
     * @return respuesta HTTP con el evento encontrado o código 404 si no existe
     */
    @GetMapping("/evento/{eventoId}")
    public ResponseEntity<?> evento(@PathVariable Long eventoId) {
        try {
            return ResponseEntity.ok(alertaService.obtenerEvento(eventoId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /**
     * Umbrales y avisos para que la interfaz explique cómo se calcula el nivel.
     *
     * @return mapa con los umbrales de lluvia, vigencia de datos y aviso informativo
     */
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
     *
     * @param req datos de la suscripción con tramo, nombre y contacto
     * @return respuesta HTTP con la suscripción guardada o código 400 si los datos son inválidos
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

    /**
     * Lista suscripciones heredadas; contiene datos de contacto, por eso exige token administrativo.
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @param token valor enviado en la cabecera {@code X-Admin-Token}
     * @return respuesta HTTP con las suscripciones del tramo o código 403 si el token no es válido
     */
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
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @return respuesta HTTP con el resultado del monitoreo, 400 si no existe el tramo o 502 si falla la evaluación
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
     *
     * @param reachId identificador del tramo en GEOGloWS
     * @param caudal caudal simulado en m³/s opcional
     * @return respuesta HTTP con el detalle simulado y el resumen del envío push
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

    // Valida el token administrativo en tiempo constante para evitar filtraciones por temporización.
    private boolean adminAutorizado(String tokenRecibido) {
        if (adminToken == null || adminToken.isBlank()) {
            return false;
        }
        byte[] esperado = adminToken.getBytes(StandardCharsets.UTF_8);
        byte[] recibido = (tokenRecibido == null ? "" : tokenRecibido).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, recibido);
    }
}
