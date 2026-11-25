package com.alerta.sistema_alerta_inundaciones.rio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Component
public class GeometriaTramoClient {

    private static final Logger log = LoggerFactory.getLogger(GeometriaTramoClient.class);
    private static final String URL_DEFECTO =
            "https://livefeeds3.arcgis.com/arcgis/rest/services/GEOGLOWS/GlobalWaterModel_Medium/MapServer/0/query";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient http;

    public GeometriaTramoClient() {
        this(URL_DEFECTO);
    }

    @Autowired
    public GeometriaTramoClient(@Value("${geoglows.geometria-url:" + URL_DEFECTO + "}") String baseUrl) {
        this.baseUrl = (baseUrl == null || baseUrl.isBlank()) ? URL_DEFECTO : baseUrl.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
    }

    public Optional<String> buscar(long reachId) {
        String url = baseUrl + "?where=comid%3D" + reachId
                + "&outFields=comid&returnGeometry=true&outSR=4326&resultRecordCount=1&f=geojson";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                log.warn("GEOGloWS geometría respondió HTTP {} para reachId={}.",
                        response.statusCode(), reachId);
                return Optional.empty();
            }
            return parsear(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Consulta de geometría interrumpida para reachId={}: {}", reachId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("No se pudo consultar la geometría para reachId={}: {}", reachId, e.getMessage());
            return Optional.empty();
        }
    }

    static Optional<String> parsear(String json) {
        if (json == null || json.isBlank()) {
            log.warn("Respuesta vacía al consultar geometría en GEOGloWS.");
            return Optional.empty();
        }
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode features = root.path("features");
            if (!features.isArray() || features.isEmpty()) {
                log.warn("Respuesta de geometría GEOGloWS sin features.");
                return Optional.empty();
            }
            JsonNode geometry = features.get(0).path("geometry");
            if (!geometry.isObject()) {
                log.warn("Feature de GEOGloWS sin objeto geometry.");
                return Optional.empty();
            }
            String type = geometry.path("type").asText("");
            JsonNode coordinates = geometry.path("coordinates");
            if (type.isBlank() || !coordinates.isArray() || coordinates.isEmpty()) {
                log.warn("Geometría de GEOGloWS inválida o vacía.");
                return Optional.empty();
            }

            if ("MultiLineString".equals(type) && coordinates.size() == 1 && coordinates.get(0).isArray()) {
                type = "LineString";
                coordinates = coordinates.get(0);
            }
            if (!coordinates.isArray() || coordinates.isEmpty()) {
                log.warn("Geometría de GEOGloWS sin coordenadas.");
                return Optional.empty();
            }

            ObjectNode salida = MAPPER.createObjectNode();
            salida.put("type", type);
            salida.set("coordinates", redondearCoordenadas(coordinates));
            return Optional.of(salida.toString());
        } catch (Exception e) {
            log.warn("No se pudo parsear la geometría de GEOGloWS: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static JsonNode redondearCoordenadas(JsonNode nodo) {
        if (nodo.isArray()) {
            ArrayNode salida = MAPPER.createArrayNode();
            for (JsonNode hijo : nodo) {
                salida.add(redondearCoordenadas(hijo));
            }
            return salida;
        }
        if (nodo.isNumber()) {
            double valor = nodo.asDouble();
            if (!Double.isFinite(valor)) {
                throw new IllegalArgumentException("Coordenada no finita");
            }
            double redondeado = BigDecimal.valueOf(valor)
                    .setScale(5, RoundingMode.HALF_UP)
                    .doubleValue();
            if (redondeado == 0.0) {
                redondeado = 0.0;
            }
            return MAPPER.getNodeFactory().numberNode(redondeado);
        }
        return nodo;
    }
}
