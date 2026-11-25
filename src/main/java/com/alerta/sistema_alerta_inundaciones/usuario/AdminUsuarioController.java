package com.alerta.sistema_alerta_inundaciones.usuario;

import com.alerta.sistema_alerta_inundaciones.auth.UsuarioDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** Gestión de usuarios y asignación de roles. Solo accesible con rol ADMIN. */
@RestController
@RequestMapping("/api/admin/usuarios")
public class AdminUsuarioController {

    private final AdminUsuarioService adminUsuarioService;

    /**
     * Crea el controlador administrativo de usuarios.
     *
     * @param adminUsuarioService servicio de administración de usuarios y roles
     */
    public AdminUsuarioController(AdminUsuarioService adminUsuarioService) {
        this.adminUsuarioService = adminUsuarioService;
    }

    /**
     * Lista todos los usuarios registrados ordenados alfabéticamente por identificador.
     * Endpoint: {@code GET /api/admin/usuarios}. Acceso: ADMIN.
     *
     * @return lista de usuarios en formato DTO
     */
    @GetMapping
    public List<UsuarioDTO> listarUsuarios() {
        return adminUsuarioService.listarUsuarios();
    }

    /**
     * Actualiza el rol asignado a un usuario existente (entre USER e INVESTIGADOR).
     * Endpoint: {@code PUT /api/admin/usuarios/{id}/rol}. Acceso: ADMIN.
     *
     * @param id        identificador del usuario a modificar
     * @param solicitud nuevo rol solicitado
     * @return usuario actualizado con su nuevo rol
     */
    @PutMapping("/{id}/rol")
    public ResponseEntity<UsuarioDTO> cambiarRol(@PathVariable Long id,
                                                 @RequestBody(required = false) CambiarRolRequest solicitud) {
        return ResponseEntity.ok(adminUsuarioService.cambiarRol(id, solicitud));
    }

    /**
     * Transforma una {@link ResponseStatusException} en respuesta HTTP con su motivo en texto plano.
     *
     * @param e excepción con código de estado y motivo
     * @return respuesta HTTP con el código y mensaje de error
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> errorEstado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }
}
