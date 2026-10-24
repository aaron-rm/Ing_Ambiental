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
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP de la consola. Consume únicamente la API REST del backend,
 * igual que lo hará el frontend definitivo. No accede a repositorios ni replica
 * reglas de negocio.
 */
@Component
public class BackendApiClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private volatile String token;

    public BackendApiClient(@Value("${alerta.api.base-url:http://localhost:8081}") String baseUrl) {
        this.mapper = new ObjectMapper().findAndRegisterModules();
        this.baseUrl = quitarBarraFinal(baseUrl);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public boolean tieneSesion() {
        return token != null && !token.isBlank();
    }

    public String getToken() {
        return token;
    }

    public SesionApi iniciarSesion(String identificador, String password) {
        SesionApi sesion = post("/api/auth/login",
                Map.of("identificador", identificador, "password", password), SesionApi.class);
        this.token = sesion.token();
        return sesion;
    }

    public SesionApi registrarse(String identificador, String password) {
        SesionApi sesion = post("/api/auth/registro",
                Map.of("identificador", identificador, "password", password), SesionApi.class);
        this.token = sesion.token();
        return sesion;
    }

    public UsuarioApi sesionActual() {
        return get("/api/auth/me", UsuarioApi.class);
    }

    public void cerrarSesion() {
        this.token = null;
    }

    public List<RioApi> listarRios() {
        return get("/api/rios", new TypeReference<List<RioApi>>() { });
    }

    public List<RioSeguidoApi> listarRiosSeguidos() {
        return get("/api/usuarios/me/rios", new TypeReference<List<RioSeguidoApi>>() { });
    }

    public SeleccionApi seguirRio(Long rioId) {
        return post("/api/usuarios/me/rios/" + rioId, null, SeleccionApi.class);
    }

    public void dejarDeSeguirRio(Long rioId) {
        delete("/api/usuarios/me/rios/" + rioId);
    }

    public AlertaRioDTO consultarEstadoPorRio(Long rioId) {
        return get("/api/alertas/rio/" + rioId, AlertaRioDTO.class);
    }

    public AlertaRioDTO consultarEstado(long reachId) {
        return get("/api/alertas/tramo/" + reachId, AlertaRioDTO.class);
    }

    public ResultadoSimulacionApi simular(long reachId, double caudal) {
        String ruta = String.format(java.util.Locale.US,
                "/api/alertas/simular?reachId=%d&caudal=%.2f", reachId, caudal);
        return post(ruta, null, ResultadoSimulacionApi.class);
    }

    public ResultadoDemoPush enviarDemoPush() {
        return post("/api/admin/push/demo", null, ResultadoDemoPush.class);
    }

    private <T> T get(String ruta, Class<T> tipo) {
        return enviar(base(ruta).GET().build(), tipo);
    }

    private <T> T get(String ruta, TypeReference<T> tipo) {
        return enviar(base(ruta).GET().build(), tipo);
    }

    private <T> T post(String ruta, Object cuerpo, Class<T> tipo) {
        return enviar(conCuerpo(base(ruta), "POST", cuerpo), tipo);
    }

    private void delete(String ruta) {
        ejecutar(conCuerpo(base(ruta), "DELETE", null));
    }

    private HttpRequest.Builder base(String ruta) {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json");
        if (tieneSesion()) {
            constructor.header("Authorization", "Bearer " + token);
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

    public record SesionApi(String token, UsuarioApi usuario) {
    }

    public record UsuarioApi(Long id, String identificador, String rol) {
    }

    public record RioApi(Long rioId, String nombre, Long reachId, Double latitud,
                         Double longitud, boolean tieneGeometria) {
    }

    public record RioSeguidoApi(Long rioId, String nombre, Long reachId, Double latitud,
                                Double longitud, Instant fechaSeleccion) {
    }

    public record SeleccionApi(Long rioId, boolean seguido, String mensaje) {
    }

    public record ResultadoSimulacionApi(AlertaRioDTO alerta, int dispositivos,
                                         int enviados, int fallidos) {
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
