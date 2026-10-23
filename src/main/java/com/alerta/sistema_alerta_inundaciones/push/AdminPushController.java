package com.alerta.sistema_alerta_inundaciones.push;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/push")
public class AdminPushController {

    private final PushService pushService;

    public AdminPushController(PushService pushService) {
        this.pushService = pushService;
    }

    @PostMapping("/demo")
    public ResponseEntity<?> enviarDemo() {
        try {
            return ResponseEntity.ok(pushService.enviarDemoATodos());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(e.getMessage());
        }
    }
}
