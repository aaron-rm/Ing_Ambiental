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

/**
 * Controlador REST para consultar el catálogo de ríos, obtener su capa GeoJSON
 * para mapas y permitir la creación de nuevos ríos a partir de coordenadas.
 */
@RestController
@RequestMapping("/api/rios")
public class RioController {

    private final RioService rioService;

    /**
     * Crea el controlador de ríos.
     *
     * @param rioService servicio de negocio de ríos
     */
    public RioController(RioService rioService) {
        this.rioService = rioService;
    }

    /**
     * Lista el resumen de todos los ríos disponibles en el catálogo.
     * Endpoint: {@code GET /api/rios}. Acceso: público.
     *
     * @return lista de resúmenes de ríos
     */
    @GetMapping
    public List<RioResumenDTO> obtenerRios() {
        return rioService.obtenerTodos();
    }

    /**
     * Crea un nuevo río buscando en GEOGloWS el tramo más cercano a las coordenadas indicadas.
     * Endpoint: {@code POST /api/rios}. Acceso: usuario con cuenta.
     *
     * @param solicitud nombre y coordenadas del río a registrar
     * @param usuario   usuario autenticado que realiza el alta
     * @return respuesta 201 con el resumen del río creado o 401 si no hay sesión
     */
    @PostMapping
    public ResponseEntity<RioResumenDTO> crearRio(@RequestBody(required = false) CrearRioRequest solicitud,
                                                  @AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(rioService.crearRio(solicitud, usuario));
    }

    /**
     * Devuelve una colección GeoJSON ({@code FeatureCollection}) con las geometrías de los ríos del catálogo.
     * Endpoint: {@code GET /api/rios/mapa}. Acceso: público.
     *
     * @return respuesta GeoJSON con las geometrías y propiedades de cada río
     */
    @GetMapping(value = "/mapa", produces = "application/geo+json")
    public ResponseEntity<String> obtenerMapa() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/geo+json"))
                .body(rioService.obtenerMapaGeoJson());
    }

    /**
     * Maneja excepciones de estado HTTP lanzadas por el servicio de ríos.
     *
     * @param e excepción con código de estado y motivo
     * @return respuesta HTTP con el código y mensaje correspondientes
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> errorEstado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }
}
