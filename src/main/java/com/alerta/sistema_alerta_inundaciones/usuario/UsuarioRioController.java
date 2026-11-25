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

@RestController
@RequestMapping("/api/usuarios/me/rios")
public class UsuarioRioController {

    private final UsuarioRioService seleccionService;

    public UsuarioRioController(UsuarioRioService seleccionService) {
        this.seleccionService = seleccionService;
    }

    @GetMapping
    public ResponseEntity<List<RioSeguidoDTO>> listar(@AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(seleccionService.listar(usuario));
    }

    @PostMapping("/{rioId}")
    public ResponseEntity<SeleccionRioResponse> seguir(@AuthenticationPrincipal Usuario usuario,
                                                       @PathVariable Long rioId) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(seleccionService.seguir(usuario, rioId));
    }

    @DeleteMapping("/{rioId}")
    public ResponseEntity<Void> dejarDeSeguir(@AuthenticationPrincipal Usuario usuario,
                                              @PathVariable Long rioId) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        seleccionService.dejarDeSeguir(usuario, rioId);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> noEncontrado(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
    }
}
