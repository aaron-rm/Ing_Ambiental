package com.alerta.sistema_alerta_inundaciones.rio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeometriaTramoClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsearConvierteMultiLineStringDeUnaParteALineStringYRedondea() throws Exception {
        String respuesta = """
                {
                  "type": "FeatureCollection",
                  "features": [
                    {
                      "type": "Feature",
                      "properties": { "comid": 710039498 },
                      "geometry": {
                        "type": "MultiLineString",
                        "coordinates": [
                          [
                            [-79.522015123, 9.052624999],
                            [-79.5222064, 9.0526925]
                          ]
                        ]
                      }
                    }
                  ]
                }
                """;

        Optional<String> resultado = GeometriaTramoClient.parsear(respuesta);

        assertTrue(resultado.isPresent());
        JsonNode geometria = mapper.readTree(resultado.get());
        assertEquals(2, geometria.size());
        assertEquals("LineString", geometria.get("type").asText());
        JsonNode coords = geometria.get("coordinates");
        assertEquals(2, coords.size());
        assertEquals(-79.52202, coords.get(0).get(0).asDouble());
        assertEquals(9.05262, coords.get(0).get(1).asDouble());
        assertEquals(-79.52221, coords.get(1).get(0).asDouble());
        assertEquals(9.05269, coords.get(1).get(1).asDouble());
    }

    @Test
    void parsearFeaturesVaciasDevuelveEmpty() {
        String respuesta = """
                {
                  "type": "FeatureCollection",
                  "features": []
                }
                """;

        assertTrue(GeometriaTramoClient.parsear(respuesta).isEmpty());
    }

    @Test
    void parsearJsonRotoDevuelveEmpty() {
        assertTrue(GeometriaTramoClient.parsear("{no es json").isEmpty());
    }
}
