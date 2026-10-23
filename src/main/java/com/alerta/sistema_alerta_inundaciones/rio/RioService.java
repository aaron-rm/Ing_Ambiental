package com.alerta.sistema_alerta_inundaciones.rio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RioService {

    private final RioRepository rioRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public RioService(RioRepository rioRepository) {
        this.rioRepository = rioRepository;
    }

    public Rio guardarRio(Rio rio) {
        return rioRepository.save(rio);
    }

    public List<RioResumenDTO> obtenerTodos() {
        return rioRepository.findAll().stream()
                .map(RioResumenDTO::desde)
                .toList();
    }

    public Rio obtenerPorId(Long id) {
        return rioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe un río con ID " + id + "."));
    }

    /** Construye un FeatureCollection GeoJSON con la geometría guardada de cada río. */
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
