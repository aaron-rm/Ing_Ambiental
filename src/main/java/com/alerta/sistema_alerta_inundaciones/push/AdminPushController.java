package com.alerta.sistema_alerta_inundaciones.push;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST de operaciones administrativas de push. Expone el envío de
 * la notificación de demostración a todos los dispositivos activos.
 */
@RestController
@RequestMapping("/api/admin/push")
public class AdminPushController {

    private final PushService pushService;

    /**
     * Crea el controlador.
     *
     * @param pushService servicio que envía las notificaciones push
     */
    public AdminPushController(PushService pushService) {
        this.pushService = pushService;
    }

    /**
     * Envía la notificación de demostración a todos los dispositivos activos.
     *
     * @return 200 con el {@link ResultadoDemoPush}, o 409 con el motivo si FCM no está configurado
     */
    @PostMapping("/demo")
    public ResponseEntity<?> enviarDemo() {
        try {
            return ResponseEntity.ok(pushService.enviarDemoATodos());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }
}
