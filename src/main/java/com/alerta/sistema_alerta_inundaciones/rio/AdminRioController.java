package com.alerta.sistema_alerta_inundaciones.rio;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mantenimiento del catálogo. Solo accesible con rol ADMIN. */
@RestController
@RequestMapping("/api/admin/rios")
public class AdminRioController {

    private final RioService rioService;

    public AdminRioController(RioService rioService) {
        this.rioService = rioService;
    }

    @PostMapping
    public ResponseEntity<Rio> crearRio(@RequestBody Rio rio) {
        return ResponseEntity.ok(rioService.guardarRio(rio));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarRio(@PathVariable Long id, @RequestBody Rio rio) {
        try {
            return ResponseEntity.ok(rioService.actualizarRio(id, rio));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }
}
