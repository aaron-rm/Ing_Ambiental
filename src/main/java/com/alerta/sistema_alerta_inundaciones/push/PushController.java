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

/**
 * Controlador REST del canal push para usuarios autenticados: entrega la
 * configuración pública de Firebase, registra y desactiva dispositivos y
 * permite enviar una notificación de prueba.
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService pushService;
    private final String adminToken;

    /**
     * Crea el controlador.
     *
     * @param pushService servicio de negocio del canal push
     * @param adminToken token administrativo heredado (vacío si no se configuró)
     */
    public PushController(PushService pushService,
                          @Value("${alerta.admin.token:}") String adminToken) {
        this.pushService = pushService;
        this.adminToken = adminToken;
    }

    /**
     * Datos públicos de Firebase; nunca incluye la credencial administrativa.
     *
     * @return configuración pública para inicializar Firebase en la PWA
     */
    @GetMapping("/config")
    public ConfigPushDTO configuracion() {
        return pushService.configuracion();
    }

    /**
     * Registra (o actualiza) el dispositivo del usuario autenticado para recibir push.
     *
     * @param usuario usuario autenticado
     * @param solicitud datos del dispositivo (token, deviceId y plataforma)
     * @return 200 con el registro, 401 sin sesión, 400 si los datos son inválidos
     *         o 409 si el token pertenece a otro dispositivo
     */
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

    /**
     * Desactiva la suscripción push de un dispositivo del usuario autenticado.
     *
     * @param usuario usuario autenticado
     * @param solicitud token y deviceId del dispositivo a desactivar
     * @return 204 si se desactivó, 401 sin sesión, 400 si no existe la suscripción
     *         o 409 si el token no pertenece al dispositivo o usuario
     */
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

    /**
     * Envía una notificación de prueba al dispositivo del usuario autenticado.
     *
     * @param usuario usuario autenticado
     * @param solicitud token y deviceId del dispositivo destino
     * @return 200 si se envió; 410 si el token es inválido; 502 si FCM falló;
     *         401 sin sesión; 400 si no hay dispositivo; 429 si se excede el límite
     *         de frecuencia; 409 si la suscripción está inactiva o no coincide
     */
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
     *
     * @param token valor del encabezado {@code X-Admin-Token}
     * @return 200 con el {@link ResultadoDemoPush}, 403 si el token no es válido
     *         o 409 si FCM no está configurado
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

    // Compara en tiempo constante el token recibido con el configurado; sin token configurado, deniega.
    private boolean adminAutorizado(String tokenRecibido) {
        if (adminToken == null || adminToken.isBlank()) {
            return false;
        }
        byte[] esperado = adminToken.getBytes(StandardCharsets.UTF_8);
        byte[] recibido = (tokenRecibido == null ? "" : tokenRecibido).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(esperado, recibido);
    }
}
