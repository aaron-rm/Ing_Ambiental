package com.alerta.sistema_alerta_inundaciones.rio;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final EliminacionRioService eliminacionRioService;

    /**
     * Crea el controlador administrativo de ríos.
     *
     * @param rioService            servicio de gestión de ríos
     * @param eliminacionRioService servicio de eliminación en cascada de ríos
     */
    public AdminRioController(RioService rioService, EliminacionRioService eliminacionRioService) {
        this.rioService = rioService;
        this.eliminacionRioService = eliminacionRioService;
    }

    /**
     * Registra directamente un río en el catálogo administrativo.
     * Endpoint: {@code POST /api/admin/rios}. Acceso: ADMIN.
     *
     * @param rio entidad del río a guardar
     * @return respuesta con el río persistido
     */
    @PostMapping
    public ResponseEntity<Rio> crearRio(@RequestBody Rio rio) {
        return ResponseEntity.ok(rioService.guardarRio(rio));
    }

    /**
     * Actualiza los datos de un río existente del catálogo.
     * Endpoint: {@code PUT /api/admin/rios/{id}}. Acceso: ADMIN.
     *
     * @param id  identificador del río a actualizar
     * @param rio datos actualizados del río
     * @return respuesta con el río actualizado o estado 404 si no existe
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> actualizarRio(@PathVariable Long id, @RequestBody Rio rio) {
        try {
            return ResponseEntity.ok(rioService.actualizarRio(id, rio));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    /**
     * Elimina un río del catálogo junto con sus muestras y suscripciones asociadas.
     * Endpoint: {@code DELETE /api/admin/rios/{id}}. Acceso: ADMIN.
     *
     * @param id identificador del río a eliminar
     * @return respuesta 204 sin contenido o estado 404 si el río no existe
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminarRio(@PathVariable Long id) {
        try {
            eliminacionRioService.eliminar(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }
}
