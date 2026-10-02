package com.alerta.sistema_alerta_inundaciones.alerta;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/alertas")
public class AlertaController {

    private final AlertaService alertaService;

    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    /**
     * Consulta el estado de alerta y pronóstico de un tramo directamente por reachId de GEOGloWS.
     */
    @GetMapping("/tramo/{reachId}")
    public ResponseEntity<?> consultarAlertaPorTramo(@PathVariable Long reachId) {
        try {
            AlertaRioDTO alerta = alertaService.consultarAlertaTramo(reachId, "Tramo GEOGLOWS " + reachId);
            return ResponseEntity.ok(alerta);
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(502).body("Error al consultar GEOGLOWS: " + e.getMessage());
        }
    }

    /**
     * Consulta el estado de alerta a partir del ID de un Río persistido en la BD.
     */
    @GetMapping("/rio/{rioId}")
    public ResponseEntity<?> consultarAlertaPorRio(@PathVariable Long rioId) {
        try {
            AlertaRioDTO alerta = alertaService.consultarAlertaPorRioId(rioId);
            return ResponseEntity.ok(alerta);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IOException | InterruptedException e) {
            return ResponseEntity.status(502).body("Error al consultar GEOGLOWS: " + e.getMessage());
        }
    }

    /**
     * Suscribe un contacto (correo o teléfono) para recibir avisos de alerta de un río.
     */
    @PostMapping("/suscribir")
    public ResponseEntity<SuscripcionAlerta> suscribir(@RequestBody SuscripcionAlerta req) {
        SuscripcionAlerta guardada = alertaService.suscribirUsuario(req.getReachId(), req.getNombreContacto(), req.getContacto());
        return ResponseEntity.ok(guardada);
    }

    /**
     * Lista las suscripciones registradas para un tramo.
     */
    @GetMapping("/suscripciones/{reachId}")
    public ResponseEntity<List<SuscripcionAlerta>> obtenerSuscripciones(@PathVariable Long reachId) {
        return ResponseEntity.ok(alertaService.obtenerSuscripcionesPorTramo(reachId));
    }

    /**
     * Simulación de inundación inyectando un caudal artificial.
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
}
