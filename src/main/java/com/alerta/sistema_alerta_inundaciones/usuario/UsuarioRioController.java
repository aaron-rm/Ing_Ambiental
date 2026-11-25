package com.alerta.sistema_alerta_inundaciones.usuario;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Controlador REST para que el usuario autenticado consulte, agregue o elimine
 * los ríos que sigue para recibir notificaciones de alerta.
 */
@RestController
@RequestMapping("/api/usuarios/me/rios")
public class UsuarioRioController {

    private final UsuarioRioService seleccionService;

    /**
     * Crea el controlador de selección de ríos por usuario.
     *
     * @param seleccionService servicio de gestión de ríos seguidos
     */
    public UsuarioRioController(UsuarioRioService seleccionService) {
        this.seleccionService = seleccionService;
    }

    /**
     * Lista todos los ríos que el usuario autenticado sigue actualmente.
     * Endpoint: {@code GET /api/usuarios/me/rios}. Acceso: usuario con cuenta.
     *
     * @param usuario usuario autenticado
     * @return lista de ríos seguidos o estado 401 si no hay sesión
     */
    @GetMapping
    public ResponseEntity<List<RioSeguidoDTO>> listar(@AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(seleccionService.listar(usuario));
    }

    /**
     * Agrega un río del catálogo a la lista de seguimiento del usuario autenticado.
     * Endpoint: {@code POST /api/usuarios/me/rios/{rioId}}. Acceso: usuario con cuenta.
     *
     * @param usuario usuario autenticado
     * @param rioId   identificador del río a seguir
     * @return respuesta indicando que el río quedó seguido o 401 si no hay sesión
     */
    @PostMapping("/{rioId}")
    public ResponseEntity<SeleccionRioResponse> seguir(@AuthenticationPrincipal Usuario usuario,
                                                       @PathVariable Long rioId) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(seleccionService.seguir(usuario, rioId));
    }

    /**
     * Elimina un río de la lista de seguimiento del usuario autenticado.
     * Endpoint: {@code DELETE /api/usuarios/me/rios/{rioId}}. Acceso: usuario con cuenta.
     *
     * @param usuario usuario autenticado
     * @param rioId   identificador del río a dejar de seguir
     * @return respuesta 204 sin contenido o 401 si no hay sesión
     */
    @DeleteMapping("/{rioId}")
    public ResponseEntity<Void> dejarDeSeguir(@AuthenticationPrincipal Usuario usuario,
                                              @PathVariable Long rioId) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        seleccionService.dejarDeSeguir(usuario, rioId);
        return ResponseEntity.noContent().build();
    }

    /**
     * Maneja el caso en que se intenta seguir un río inexistente.
     *
     * @param e excepción de argumento inválido
     * @return respuesta HTTP 404 con el mensaje de error
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> noEncontrado(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
