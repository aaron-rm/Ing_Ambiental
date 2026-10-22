package com.alerta.sistema_alerta_inundaciones.push;

import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService pushService;
    private final String adminToken;

    public PushController(PushService pushService,
                          @Value("${alerta.admin.token:}") String adminToken) {
        this.pushService = pushService;
        this.adminToken = adminToken;
    }

    /** Datos públicos de Firebase; nunca incluye la credencial administrativa. */
    @GetMapping("/config")
    public ConfigPushDTO configuracion() {
        return pushService.configuracion();
    }

    @PostMapping("/registrar")
    public ResponseEntity<?> registrar(@Valid @RequestBody RegistroPushRequest solicitud) {
        try {
            return ResponseEntity.ok(pushService.registrar(solicitud));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @DeleteMapping("/registrar")
    public ResponseEntity<?> desactivar(@Valid @RequestBody PruebaPushRequest solicitud) {
        try {
            pushService.desactivar(solicitud.getToken().trim(), solicitud.getDeviceId().trim());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    /**
     * Envía una alerta de simulacro a todos los dispositivos activos. Requiere
     * token administrativo para evitar que cualquiera dispare notificaciones.
     */
    @PostMapping("/demo")
    public ResponseEntity<?> enviarDemo(@RequestHeader(value = "X-Admin-Token", required = false) String token) {
        if (!adminAutorizado(token)) {
            return ResponseEntity.status(403).body(
                    "Acceso restringido. Configure ALERTA_ADMIN_TOKEN y envíelo en X-Admin-Token.");
        }
        try {
            return ResponseEntity.ok(pushService.enviarDemoATodos());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @PostMapping("/prueba")
    public ResponseEntity<?> enviarPrueba(@Valid @RequestBody PruebaPushRequest solicitud) {
        try {
            ResultadoEnvioPush resultado = pushService.enviarPrueba(solicitud);
            if (resultado.exito()) {
                return ResponseEntity.ok(resultado);
            }
            if (resultado.tokenInvalido()) {
                return ResponseEntity.status(410).body(resultado);
            }
            return ResponseEntity.status(502).body(resultado);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (LimitePruebaExcedidoException e) {
            return ResponseEntity.status(429).body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
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
