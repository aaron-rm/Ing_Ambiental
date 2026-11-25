package com.alerta.sistema_alerta_inundaciones.rio;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.ClienteApiGeoglows;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de negocio para la gestión del catálogo de ríos, alta por coordenadas
 * mediante GEOGloWS y generación del mapa GeoJSON.
 */
@Service
public class RioService {

    private final RioRepository rioRepository;
    private final ClienteApiGeoglows clienteApiGeoglows;
    private final GeometriaTramoClient geometriaTramoClient;
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Crea el servicio únicamente con el repositorio de ríos (útil en pruebas unitarias).
     *
     * @param rioRepository repositorio de ríos
     */
    public RioService(RioRepository rioRepository) {
        this(rioRepository, null, null);
    }

    /**
     * Crea el servicio con el repositorio de ríos y el cliente de GEOGloWS.
     *
     * @param rioRepository      repositorio de ríos
     * @param clienteApiGeoglows cliente para consultar tramos en GEOGloWS
     */
    public RioService(RioRepository rioRepository, ClienteApiGeoglows clienteApiGeoglows) {
        this(rioRepository, clienteApiGeoglows, null);
    }

    /**
     * Crea el servicio con todas sus dependencias para persistencia y consulta a GEOGloWS.
     *
     * @param rioRepository        repositorio de ríos
     * @param clienteApiGeoglows   cliente de la API de GEOGloWS
     * @param geometriaTramoClient cliente para descargar la geometría GeoJSON del tramo
     */
    @Autowired
    public RioService(RioRepository rioRepository,
                      ClienteApiGeoglows clienteApiGeoglows,
                      GeometriaTramoClient geometriaTramoClient) {
        this.rioRepository = rioRepository;
        this.clienteApiGeoglows = clienteApiGeoglows;
        this.geometriaTramoClient = geometriaTramoClient;
    }

    /**
     * Persiste directamente una entidad {@link Rio}.
     *
     * @param rio entidad a guardar
     * @return entidad guardada
     */
    public Rio guardarRio(Rio rio) {
        return rioRepository.save(rio);
    }

    /**
     * Crea un río a partir de coordenadas consultando el tramo más cercano en GEOGloWS.
     *
     * @param solicitud datos con nombre, latitud y longitud
     * @param usuario   usuario autenticado que registra el río (opcional)
     * @return resumen del río creado
     * @throws ResponseStatusException si los datos son inválidos (400), falla GEOGloWS (502),
     *                                 no hay tramo cercano (422) o el tramo ya existe (409)
     */
    public RioResumenDTO crearRio(CrearRioRequest solicitud, Usuario usuario) {
        if (solicitud == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "Los datos del río son obligatorios.");
        }
        String nombre = validarNombre(solicitud.nombre());
        Double latitud = solicitud.latitud();
        if (latitud == null || !Double.isFinite(latitud) || latitud < -90.0 || latitud > 90.0) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "La latitud debe estar entre -90 y 90.");
        }
        Double longitud = solicitud.longitud();
        if (longitud == null || !Double.isFinite(longitud) || longitud < -180.0 || longitud > 180.0) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "La longitud debe estar entre -180 y 180.");
        }
        if (clienteApiGeoglows == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "No se pudo consultar GEOGloWS: cliente no disponible.");
        }

        Long reachId;
        try {
            reachId = clienteApiGeoglows.buscarReachIdCercano(latitud, longitud);
        } catch (ResponseStatusException | IllegalArgumentException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "No se pudo consultar GEOGloWS: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatusCode.valueOf(502),
                    "No se pudo consultar GEOGloWS: " + e.getMessage(), e);
        }

        if (reachId == null) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(422),
                    "No se encontró un tramo de GEOGloWS cerca de esas coordenadas.");
        }

        Optional<Rio> existente = rioRepository.findFirstByGeoglowsId(reachId);
        if (existente.isPresent()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(409),
                    "Ya existe el río «" + existente.get().getNombre() + "» para ese tramo.");
        }

        String geometria = geometriaTramoClient != null
                ? geometriaTramoClient.buscar(reachId).orElse(null)
                : null;

        Rio nuevo = new Rio(nombre, reachId, latitud, longitud);
        nuevo.setGeometria(geometria);
        nuevo.setCreadoPor(usuario != null ? usuario.getIdentificador() : null);
        return RioResumenDTO.desde(rioRepository.save(nuevo));
    }

    /**
     * Cambia el nombre visible de un río y lo marca como editado a mano, para que
     * el inicializador del catálogo no lo restaure al reiniciar.
     *
     * @param id        identificador del río
     * @param solicitud cuerpo con el nuevo nombre
     * @return resumen del río con el nombre actualizado
     * @throws ResponseStatusException si el nombre es inválido (400) o el río no existe (404)
     */
    public RioResumenDTO renombrarRio(Long id, RenombrarRioRequest solicitud) {
        String nombre = validarNombre(solicitud == null ? null : solicitud.nombre());
        Rio rio = rioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatusCode.valueOf(404),
                        "No existe un río con ID " + id + "."));
        rio.setNombre(nombre);
        rio.setNombreEditado(true);
        return RioResumenDTO.desde(rioRepository.save(rio));
    }

    // Recorta el nombre y comprueba que no esté vacío ni supere 100 caracteres.
    private static String validarNombre(String valor) {
        String nombre = valor == null ? "" : valor.trim();
        if (nombre.isEmpty()) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "El nombre del río es obligatorio.");
        }
        if (nombre.length() > 100) {
            throw new ResponseStatusException(HttpStatusCode.valueOf(400),
                    "El nombre del río no puede superar 100 caracteres.");
        }
        return nombre;
    }

    /**
     * Obtiene el listado resumido de todos los ríos registrados.
     *
     * @return lista de {@link RioResumenDTO}
     */
    public List<RioResumenDTO> obtenerTodos() {
        return rioRepository.findAll().stream()
                .map(RioResumenDTO::desde)
                .toList();
    }

    /**
     * Busca un río por su identificador primario.
     *
     * @param id identificador del río
     * @return entidad {@link Rio} encontrada
     * @throws IllegalArgumentException si no existe un río con el ID indicado
     */
    public Rio obtenerPorId(Long id) {
        return rioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe un río con ID " + id + "."));
    }

    /**
     * Construye un FeatureCollection GeoJSON con la geometría guardada de cada río.
     *
     * @return cadena JSON con el {@code FeatureCollection}
     * @throws IllegalStateException si alguna geometría guardada no es un GeoJSON válido
     */
    public String obtenerMapaGeoJson() {
        ObjectNode coleccion = mapper.createObjectNode();
        coleccion.put("type", "FeatureCollection");
        ArrayNode features = coleccion.putArray("features");

        for (Rio rio : rioRepository.findAll()) {
            if (rio.getGeometria() == null || rio.getGeometria().isBlank()) {
                continue;
            }
            try {
                ObjectNode feature = features.addObject();
                feature.put("type", "Feature");
                feature.set("geometry", mapper.readTree(rio.getGeometria()));
                ObjectNode propiedades = feature.putObject("properties");
                propiedades.put("rioId", rio.getId());
                propiedades.put("nombre", rio.getNombre());
                if (rio.getGeoglowsId() != null) {
                    propiedades.put("reachId", rio.getGeoglowsId());
                }
            } catch (Exception e) {
                throw new IllegalStateException(
                        "La geometría del río " + rio.getNombre() + " no es un GeoJSON válido.", e);
            }
        }
        return coleccion.toString();
    }

    /**
     * Actualiza los datos básicos y, si se incluye, la geometría de un río existente.
     *
     * @param id    identificador del río a modificar
     * @param datos entidad con los nuevos valores
     * @return entidad {@link Rio} actualizada
     * @throws IllegalArgumentException si no existe un río con ese identificador
     */
    public Rio actualizarRio(Long id, Rio datos) {
        Rio existente = obtenerPorId(id);
        existente.setNombre(datos.getNombre());
        existente.setGeoglowsId(datos.getGeoglowsId());
        existente.setLatitud(datos.getLatitud());
        existente.setLongitud(datos.getLongitud());
        if (datos.getGeometria() != null) {
            existente.setGeometria(datos.getGeometria());
        }
        return rioRepository.save(existente);
    }
}
