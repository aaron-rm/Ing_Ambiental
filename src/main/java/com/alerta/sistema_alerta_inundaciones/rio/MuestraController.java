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

/**
 * Controlador REST para consultar y registrar muestras de laboratorio o campo
 * (individuales o por lote en CSV) asociadas a un río del catálogo.
 */
@RestController
@RequestMapping("/api/rios")
public class MuestraController {

    private final MuestraService muestraService;

    /**
     * Crea el controlador de muestras.
     *
     * @param muestraService servicio de negocio de muestras
     */
    public MuestraController(MuestraService muestraService) {
        this.muestraService = muestraService;
    }

    /**
     * Lista las muestras más recientes registradas para un río.
     * Endpoint: {@code GET /api/rios/{rioId}/muestras}. Acceso: público.
     *
     * @param rioId identificador del río
     * @return lista de muestras ordenadas de más reciente a más antigua
     */
    @GetMapping("/{rioId}/muestras")
    public List<MuestraDTO> listarMuestras(@PathVariable Long rioId) {
        return muestraService.listarPorRio(rioId);
    }

    /**
     * Registra una nueva muestra individual asociada a un río.
     * Endpoint: {@code POST /api/rios/{rioId}/muestras}. Acceso: ADMIN/INVESTIGADOR.
     *
     * @param rioId     identificador del río
     * @param solicitud datos de la muestra a registrar
     * @param usuario   usuario autenticado responsable del registro
     * @return respuesta 201 con la muestra creada o 401 si no hay usuario autenticado
     */
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

    /**
     * Carga e inserta un lote de muestras desde un contenido en formato CSV (máximo 1 MB).
     * Endpoint: {@code POST /api/rios/{rioId}/muestras/csv}. Acceso: ADMIN/INVESTIGADOR.
     *
     * @param rioId     identificador del río
     * @param contenido texto CSV con cabecera {@code fecha,parametro,valor,unidad,nota}
     * @param usuario   usuario autenticado responsable de la carga
     * @param request   petición HTTP para verificar la longitud del cuerpo
     * @return mapa con la cantidad de filas insertadas ({@code insertadas})
     * @throws ResponseStatusException si el cuerpo supera 1 MB o los datos son inválidos
     */
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

    /**
     * Transforma una {@link ResponseStatusException} en respuesta HTTP con texto plano.
     *
     * @param e excepción con código de estado y motivo
     * @return respuesta HTTP con el código y el mensaje de error
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> errorEstado(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
    }
}
