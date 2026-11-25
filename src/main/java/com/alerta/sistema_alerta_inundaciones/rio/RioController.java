package com.alerta.sistema_alerta_inundaciones.rio;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/rios")
public class RioController {

    private final RioService rioService;

    public RioController(RioService rioService) {
        this.rioService = rioService;
    }

    @GetMapping
    public List<RioResumenDTO> obtenerRios() {
        return rioService.obtenerTodos();
    }

    @PostMapping
    public ResponseEntity<RioResumenDTO> crearRio(@RequestBody(required = false) CrearRioRequest solicitud,
                                                  @AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(rioService.crearRio(solicitud, usuario));
    }

    @GetMapping(value = "/mapa", produces = "application/geo+json")
    public ResponseEntity<String> obtenerMapa() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/geo+json"))
                .body(rioService.obtenerMapaGeoJson());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> errorEstado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }
}
