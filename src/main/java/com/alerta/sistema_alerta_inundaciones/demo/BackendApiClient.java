package com.alerta.sistema_alerta_inundaciones.demo;

import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;
import com.alerta.sistema_alerta_inundaciones.push.ResultadoDemoPush;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Cliente HTTP de la consola. Consume únicamente la API REST pública del
 * backend, igual que lo hará el frontend definitivo. No accede a repositorios
 * ni replica reglas de negocio.
 */
@Component
public class BackendApiClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String adminToken;

    public BackendApiClient(
            @Value("${alerta.api.base-url:http://localhost:8081}") String baseUrl,
            @Value("${alerta.admin.token:}") String adminToken) {
        // Spring Boot 4 usa Jackson 3 para MVC, pero Firebase Admin y este
        // cliente trabajan con Jackson 2. Se crea el mapper localmente para no
        // depender de un bean que Spring Boot 4 ya no expone.
        this.mapper = new ObjectMapper().findAndRegisterModules();
        this.baseUrl = quitarBarraFinal(baseUrl);
        this.adminToken = adminToken == null ? "" : adminToken.trim();
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean tieneTokenAdmin() {
        return !adminToken.isBlank();
    }

    public List<RioApi> listarRios() {
        return get("/api/rios", new TypeReference<List<RioApi>>() { });
    }

    public RioApi crearRio(String nombre, Long geoglowsId, Double latitud, Double longitud) {
        Map<String, Object> cuerpo = Map.of(
                "nombre", nombre,
                "geoglowsId", geoglowsId,
                "latitud", latitud,
                "longitud", longitud
        );
        return post("/api/rios", cuerpo, RioApi.class, false);
    }

    public RioApi actualizarRio(RioApi rio) {
        if (rio.id() == null) {
            throw new IllegalArgumentException("El rio no tiene un ID interno para actualizar.");
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("nombre", rio.nombre());
        cuerpo.put("geoglowsId", rio.geoglowsId());
        cuerpo.put("latitud", rio.latitud());
        cuerpo.put("longitud", rio.longitud());
        return put("/api/rios/" + rio.id(), cuerpo, RioApi.class);
    }

    public AlertaRioDTO consultarEstado(long reachId) {
        return get("/api/alertas/tramo/" + reachId, AlertaRioDTO.class);
    }

    public AlertaRioDTO consultarEstadoPorRio(long rioId) {
        return get("/api/alertas/rio/" + rioId, AlertaRioDTO.class);
    }

    public AlertaRioDTO simular(long reachId, double caudal) {
        String ruta = String.format(Locale.US, "/api/alertas/simular?reachId=%d&caudal=%.2f",
                reachId, caudal);
        return post(ruta, null, AlertaRioDTO.class, false);
    }

    public SuscripcionApi suscribir(long reachId, String nombre, String contacto) {
        Map<String, Object> cuerpo = Map.of(
                "reachId", reachId,
                "nombreContacto", nombre,
                "contacto", contacto
        );
        return post("/api/alertas/suscribir", cuerpo, SuscripcionApi.class, false);
    }

    public List<SuscripcionApi> listarSuscripciones(long reachId) {
        exigirTokenAdmin();
        return get("/api/alertas/suscripciones/" + reachId,
                new TypeReference<List<SuscripcionApi>>() { }, true);
    }

    public ResultadoDemoPush enviarDemoPush() {
        exigirTokenAdmin();
        return post("/api/push/demo", null, ResultadoDemoPush.class, true);
    }

    private void exigirTokenAdmin() {
        if (!tieneTokenAdmin()) {
            throw new ApiException(403,
                    "ALERTA_ADMIN_TOKEN no esta configurado en el entorno del backend.");
        }
    }

    private <T> T get(String ruta, Class<T> tipo) {
        return get(ruta, tipo, false);
    }

    private <T> T get(String ruta, Class<T> tipo, boolean admin) {
        HttpRequest.Builder constructor = base(ruta, admin);
        return enviar(constructor.GET().build(), tipo);
    }

    private <T> T get(String ruta, TypeReference<T> tipo) {
        return get(ruta, tipo, false);
    }

    private <T> T get(String ruta, TypeReference<T> tipo, boolean admin) {
        HttpRequest.Builder constructor = base(ruta, admin);
        return enviar(constructor.GET().build(), tipo);
    }

    private <T> T post(String ruta, Object cuerpo, Class<T> tipo, boolean admin) {
        HttpRequest.Builder constructor = base(ruta, admin);
        return enviar(conCuerpo(constructor, "POST", cuerpo), tipo);
    }

    private <T> T put(String ruta, Object cuerpo, Class<T> tipo) {
        HttpRequest.Builder constructor = base(ruta, false);
        return enviar(conCuerpo(constructor, "PUT", cuerpo), tipo);
    }

    private HttpRequest.Builder base(String ruta, boolean admin) {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json");
        if (admin && !adminToken.isBlank()) {
            constructor.header("X-Admin-Token", adminToken);
        }
        return constructor;
    }

    private HttpRequest conCuerpo(HttpRequest.Builder constructor, String metodo, Object cuerpo) {
        try {
            if (cuerpo == null) {
                return constructor.method(metodo, HttpRequest.BodyPublishers.noBody()).build();
            }
            return constructor
                    .header("Content-Type", "application/json")
                    .method(metodo, HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(cuerpo), StandardCharsets.UTF_8))
                    .build();
        } catch (Exception e) {
            throw new ApiException(0, "No se pudo preparar la peticion: " + e.getMessage());
        }
    }

    private <T> T enviar(HttpRequest peticion, Class<T> tipo) {
        String cuerpo = ejecutar(peticion);
        if (cuerpo == null || cuerpo.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(cuerpo, tipo);
        } catch (Exception e) {
            throw new ApiException(0, "Respuesta inesperada del backend: " + e.getMessage());
        }
    }

    private <T> T enviar(HttpRequest peticion, TypeReference<T> tipo) {
        String cuerpo = ejecutar(peticion);
        if (cuerpo == null || cuerpo.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(cuerpo, tipo);
        } catch (Exception e) {
            throw new ApiException(0, "Respuesta inesperada del backend: " + e.getMessage());
        }
    }

    private String ejecutar(HttpRequest peticion) {
        try {
            HttpResponse<String> respuesta = http.send(peticion,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (respuesta.statusCode() >= 200 && respuesta.statusCode() < 300) {
                return respuesta.body();
            }
            throw new ApiException(respuesta.statusCode(), mensajeError(respuesta.body()));
        } catch (ApiException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(0, "La peticion al backend fue interrumpida.");
        } catch (Exception e) {
            throw new ApiException(0, "No se pudo conectar con " + baseUrl + ": " + e.getMessage());
        }
    }

    private String mensajeError(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return "El backend respondio con un error.";
        }
        String texto = cuerpo.trim();
        try {
            JsonNode nodo = mapper.readTree(texto);
            if (nodo.isTextual()) {
                texto = nodo.asText();
            } else if (nodo.has("message")) {
                texto = nodo.get("message").asText();
            } else if (nodo.has("error")) {
                texto = nodo.get("error").asText();
            }
        } catch (Exception ignored) {
            // El backend puede devolver texto plano; se usa tal cual.
        }
        return texto.length() > 500 ? texto.substring(0, 500) : texto;
    }

    private static String quitarBarraFinal(String url) {
        if (url == null || url.isBlank()) {
            return "http://localhost:8081";
        }
        String limpia = url.trim();
        while (limpia.endsWith("/")) {
            limpia = limpia.substring(0, limpia.length() - 1);
        }
        return limpia;
    }

    public record RioApi(Long id, String nombre, Long geoglowsId, Double latitud, Double longitud) {
    }

    public record SuscripcionApi(Long id, Long reachId, String nombreContacto,
                                 String contacto, LocalDateTime fechaRegistro) {
    }

    /** Error HTTP o de conexion con el backend, con el codigo disponible. */
    public static class ApiException extends RuntimeException {

        private final int estado;

        public ApiException(int estado, String mensaje) {
            super(mensaje);
            this.estado = estado;
        }

        public int getEstado() {
            return estado;
        }
    }
}
