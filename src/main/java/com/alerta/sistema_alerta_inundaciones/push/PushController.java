package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    public ResponseEntity<?> registrar(@AuthenticationPrincipal Usuario usuario,
                                       @Valid @RequestBody RegistroPushRequest solicitud) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(pushService.registrar(usuario, solicitud));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @DeleteMapping("/registrar")
    public ResponseEntity<?> desactivar(@AuthenticationPrincipal Usuario usuario,
                                        @Valid @RequestBody PruebaPushRequest solicitud) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            pushService.desactivar(usuario, solicitud.getToken().trim(), solicitud.getDeviceId().trim());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }

    @PostMapping("/prueba")
    public ResponseEntity<?> enviarPrueba(@AuthenticationPrincipal Usuario usuario,
                                          @Valid @RequestBody PruebaPushRequest solicitud) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            ResultadoEnvioPush resultado = pushService.enviarPrueba(usuario, solicitud);
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

    /**
     * Compatibilidad temporal con la consola antigua. Se retirará cuando la
     * consola termine de migrar a JWT; el frontend debe usar
     * {@code POST /api/admin/push/demo}.
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

    private boolean adminAutorizado(String tokenRecibido) {
        if (adminToken == null || adminToken.isBlank()) {
            return false;
        }
        byte[] esperado = adminToken.getBytes(StandardCharsets.UTF_8);
        byte[] recibido = (tokenRecibido == null ? "" : tokenRecibido).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, recibido);
    }
}
