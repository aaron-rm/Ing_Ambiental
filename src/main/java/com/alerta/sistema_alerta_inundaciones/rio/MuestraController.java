package com.alerta.sistema_alerta_inundaciones.rio;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rios")
public class MuestraController {

    private final MuestraService muestraService;

    public MuestraController(MuestraService muestraService) {
        this.muestraService = muestraService;
    }

    @GetMapping("/{rioId}/muestras")
    public List<MuestraDTO> listarMuestras(@PathVariable Long rioId) {
        return muestraService.listarPorRio(rioId);
    }

    @PostMapping("/{rioId}/muestras")
    public ResponseEntity<MuestraDTO> crearMuestra(@PathVariable Long rioId,
                                                   @RequestBody(required = false) CrearMuestraRequest solicitud,
                                                   @AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(muestraService.crearMuestra(rioId, solicitud, usuario));
    }

    @PostMapping(value = "/{rioId}/muestras/csv", consumes = {"text/csv", "text/plain"})
    public ResponseEntity<Map<String, Integer>> cargarCsv(@PathVariable Long rioId,
                                                          @RequestBody(required = false) String contenido,
                                                          @AuthenticationPrincipal Usuario usuario,
                                                          HttpServletRequest request) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (request != null && request.getContentLengthLong() > 1_048_576L) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(413), "El CSV supera 1 MB.");
        }
        int insertadas = muestraService.cargarCsv(rioId, contenido, usuario);
        return ResponseEntity.ok(Map.of("insertadas", insertadas));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> errorEstado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }
}
