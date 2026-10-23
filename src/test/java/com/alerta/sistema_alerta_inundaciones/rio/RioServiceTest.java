package com.alerta.sistema_alerta_inundaciones.rio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RioServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void obtenerMapaGeoJsonIncluyeRioIdNombreYGeometria() throws Exception {
        RioRepository repositorio = mock(RioRepository.class);
        Rio rio = new Rio("Río Abajo", 710039498L, 9.0, -79.48);
        rio.setGeometria("{\"type\":\"LineString\",\"coordinates\":[[-79.48,9.0],[-79.46,9.01]]}");
        when(repositorio.findAll()).thenReturn(List.of(rio));

        String geojson = new RioService(repositorio).obtenerMapaGeoJson();

        JsonNode coleccion = mapper.readTree(geojson);
        assertEquals("FeatureCollection", coleccion.get("type").asText());
        JsonNode feature = coleccion.get("features").get(0);
        assertEquals(710039498L, feature.get("properties").get("reachId").asLong());
        assertEquals("Río Abajo", feature.get("properties").get("nombre").asText());
        assertEquals("LineString", feature.get("geometry").get("type").asText());
    }

    @Test
    void obtenerMapaOmiteLosRiosSinGeometria() throws Exception {
        RioRepository repositorio = mock(RioRepository.class);
        Rio conGeometria = new Rio("Río Tocumen", 670053261L, 9.03, -79.4);
        conGeometria.setGeometria("{\"type\":\"LineString\",\"coordinates\":[[-79.4,9.03]]}");
        Rio sinGeometria = new Rio("Río sin geometría", 1L, 9.0, -79.0);
        when(repositorio.findAll()).thenReturn(List.of(conGeometria, sinGeometria));

        JsonNode coleccion = mapper.readTree(new RioService(repositorio).obtenerMapaGeoJson());

        assertThat(coleccion.get("features")).hasSize(1);
        assertEquals("Río Tocumen", coleccion.get("features").get(0).get("properties").get("nombre").asText());
    }

    @Test
    void obtenerPorIdFallaConMensajeClaro() {
        RioRepository repositorio = mock(RioRepository.class);
        when(repositorio.findById(7L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RioService(repositorio).obtenerPorId(7L));

        assertThat(error.getMessage()).contains("7");
    }
}
