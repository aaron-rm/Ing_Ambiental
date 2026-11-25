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

    /**
     * Crea el cliente apuntando al backend.
     *
     * @param baseUrl URL base del backend (por defecto {@code http://localhost:8081})
     */
    public BackendApiClient(@Value("${alerta.api.base-url:http://localhost:8081}") String baseUrl) {
        this.mapper = new ObjectMapper().findAndRegisterModules();
        this.baseUrl = quitarBarraFinal(baseUrl);
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Obtiene la URL base del backend, sin barra final.
     *
     * @return URL base normalizada
     */
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Indica si hay una sesión iniciada (token disponible).
     *
     * @return {@code true} si existe un token no vacío
     */
    public boolean tieneSesion() {
        return token != null && !token.isBlank();
    }

    /**
     * Obtiene el token de sesión actual.
     *
     * @return token JWT, o {@code null} si no hay sesión
     */
    public String getToken() {
        return token;
    }

    /**
     * Inicia sesión en el backend y guarda el token recibido.
     *
     * @param identificador correo o nombre de usuario
     * @param password contraseña
     * @return sesión con el token y los datos del usuario
     * @throws ApiException si las credenciales son inválidas o falla la conexión
     */
    public SesionApi iniciarSesion(String identificador, String password) {
        SesionApi sesion = post("/api/auth/login",
                Map.of("identificador", identificador, "password", password), SesionApi.class);
        this.token = sesion.token();
        return sesion;
    }

    /**
     * Registra un usuario nuevo (rol USER) y deja la sesión iniciada.
     *
     * @param identificador correo o nombre de usuario
     * @param password contraseña
     * @return sesión con el token y los datos del usuario
     * @throws ApiException si el identificador ya existe, los datos son inválidos o falla la conexión
     */
    public SesionApi registrarse(String identificador, String password) {
        SesionApi sesion = post("/api/auth/registro",
                Map.of("identificador", identificador, "password", password), SesionApi.class);
        this.token = sesion.token();
        return sesion;
    }

    /**
     * Consulta los datos del usuario autenticado.
     *
     * @return usuario de la sesión actual
     * @throws ApiException si el token es inválido o falla la conexión
     */
    public UsuarioApi sesionActual() {
        return get("/api/auth/me", UsuarioApi.class);
    }

    /** Cierra la sesión local descartando el token. */
    public void cerrarSesion() {
        this.token = null;
    }

    /**
     * Lista el catálogo de ríos.
     *
     * @return ríos disponibles
     * @throws ApiException si falla la petición
     */
    public List<RioApi> listarRios() {
        return get("/api/rios", new TypeReference<List<RioApi>>() { });
    }

    /**
     * Lista los ríos que sigue el usuario autenticado.
     *
     * @return ríos seguidos
     * @throws ApiException si falla la petición o no hay sesión válida
     */
    public List<RioSeguidoApi> listarRiosSeguidos() {
        return get("/api/usuarios/me/rios", new TypeReference<List<RioSeguidoApi>>() { });
    }

    /**
     * Hace que el usuario autenticado siga un río.
     *
     * @param rioId identificador del río
     * @return resultado de la selección
     * @throws ApiException si falla la petición o el río no existe
     */
    public SeleccionApi seguirRio(Long rioId) {
        return post("/api/usuarios/me/rios/" + rioId, null, SeleccionApi.class);
    }

    /**
     * Hace que el usuario autenticado deje de seguir un río.
     *
     * @param rioId identificador del río
     * @throws ApiException si falla la petición
     */
    public void dejarDeSeguirRio(Long rioId) {
        delete("/api/usuarios/me/rios/" + rioId);
    }

    /**
     * Consulta el estado de alerta (caudal y lluvia) de un río del catálogo.
     *
     * @param rioId identificador del río
     * @return estado de alerta del río
     * @throws ApiException si falla la petición
     */
    public AlertaRioDTO consultarEstadoPorRio(Long rioId) {
        return get("/api/alertas/rio/" + rioId, AlertaRioDTO.class);
    }

    /**
     * Consulta el estado de alerta de un tramo de GEOGloWS.
     *
     * @param reachId identificador del tramo (reach_id)
     * @return estado de alerta del tramo
     * @throws ApiException si falla la petición
     */
    public AlertaRioDTO consultarEstado(long reachId) {
        return get("/api/alertas/tramo/" + reachId, AlertaRioDTO.class);
    }

    /**
     * Simula una crecida en un tramo y envía push a los seguidores (solo ADMIN).
     *
     * @param reachId identificador del tramo (reach_id)
     * @param caudal caudal simulado en m3/s
     * @return alerta resultante y conteo de dispositivos, enviados y fallidos
     * @throws ApiException si falla la petición o el rol no es suficiente
     */
    public ResultadoSimulacionApi simular(long reachId, double caudal) {
        String ruta = String.format(java.util.Locale.US,
                "/api/alertas/simular?reachId=%d&caudal=%.2f", reachId, caudal);
        return post(ruta, null, ResultadoSimulacionApi.class);
    }

    /**
     * Envía la notificación push de demostración a todos los dispositivos activos (solo ADMIN).
     *
     * @return resumen de dispositivos, enviados y fallidos
     * @throws ApiException si falla la petición, FCM no está configurado o el rol no es suficiente
     */
    public ResultadoDemoPush enviarDemoPush() {
        return post("/api/admin/push/demo", null, ResultadoDemoPush.class);
    }

    // GET que deserializa la respuesta a una clase simple.
    private <T> T get(String ruta, Class<T> tipo) {
        return enviar(base(ruta).GET().build(), tipo);
    }

    // GET que deserializa la respuesta a un tipo genérico (por ejemplo, listas).
    private <T> T get(String ruta, TypeReference<T> tipo) {
        return enviar(base(ruta).GET().build(), tipo);
    }

    // POST con cuerpo JSON opcional que deserializa la respuesta.
    private <T> T post(String ruta, Object cuerpo, Class<T> tipo) {
        return enviar(conCuerpo(base(ruta), "POST", cuerpo), tipo);
    }

    // DELETE sin cuerpo; ignora la respuesta.
    private void delete(String ruta) {
        ejecutar(conCuerpo(base(ruta), "DELETE", null));
    }

    // Prepara la petición base: URL, timeout, Accept y token Bearer si hay sesión.
    private HttpRequest.Builder base(String ruta) {
        HttpRequest.Builder constructor = HttpRequest.newBuilder(URI.create(baseUrl + ruta))
                .timeout(Duration.ofSeconds(60))
                .header("Accept", "application/json");
        if (tieneSesion()) {
            constructor.header("Authorization", "Bearer " + token);
        }
        return constructor;
    }

    // Construye la petición con método HTTP y cuerpo JSON (o sin cuerpo si es null).
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

    // Ejecuta la petición y convierte el JSON de respuesta a la clase indicada.
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

    // Ejecuta la petición y convierte el JSON de respuesta a un tipo genérico.
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

    // Envía la petición; devuelve el cuerpo si es 2xx o lanza ApiException con el estado.
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

    // Extrae un mensaje legible del cuerpo de error (texto, "message" o "error"), limitado a 500 caracteres.
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

    // Quita las barras finales de la URL; si está vacía, usa http://localhost:8081.
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

    /**
     * Sesión devuelta por el backend al iniciar sesión o registrarse.
     *
     * @param token token JWT de la sesión
     * @param usuario datos del usuario autenticado
     */
    public record SesionApi(String token, UsuarioApi usuario) {
    }

    /**
     * Datos básicos de un usuario del backend.
     *
     * @param id identificador del usuario
     * @param identificador correo o nombre de usuario
     * @param rol rol del usuario (USER o ADMIN)
     */
    public record UsuarioApi(Long id, String identificador, String rol) {
    }

    /**
     * Río del catálogo.
     *
     * @param rioId identificador del río
     * @param nombre nombre del río
     * @param reachId identificador del tramo en GEOGloWS
     * @param latitud latitud de referencia
     * @param longitud longitud de referencia
     * @param tieneGeometria {@code true} si el río tiene geometría cargada
     */
    public record RioApi(Long rioId, String nombre, Long reachId, Double latitud,
                         Double longitud, boolean tieneGeometria) {
    }

    /**
     * Río seguido por el usuario autenticado.
     *
     * @param rioId identificador del río
     * @param nombre nombre del río
     * @param reachId identificador del tramo en GEOGloWS
     * @param latitud latitud de referencia
     * @param longitud longitud de referencia
     * @param fechaSeleccion fecha en que el usuario comenzó a seguir el río
     */
    public record RioSeguidoApi(Long rioId, String nombre, Long reachId, Double latitud,
                                Double longitud, Instant fechaSeleccion) {
    }

    /**
     * Resultado de seguir un río.
     *
     * @param rioId identificador del río seleccionado
     * @param seguido {@code true} si el río quedó seguido
     * @param mensaje mensaje descriptivo de la operación
     */
    public record SeleccionApi(Long rioId, boolean seguido, String mensaje) {
    }

    /**
     * Resultado de una simulación administrativa de crecida con envío de push.
     *
     * @param alerta estado de alerta generado por la simulación
     * @param dispositivos cantidad de dispositivos seguidores objetivo
     * @param enviados cantidad de notificaciones entregadas a FCM
     * @param fallidos cantidad de envíos que fallaron
     */
    public record ResultadoSimulacionApi(AlertaRioDTO alerta, int dispositivos,
                                         int enviados, int fallidos) {
    }

    /** Error HTTP o de conexion con el backend, con el codigo disponible. */
    public static class ApiException extends RuntimeException {

        private final int estado;

        /**
         * Crea la excepción con el código de estado HTTP y el mensaje de error.
         *
         * @param estado código HTTP recibido (0 si fue error de conexión o local)
         * @param mensaje descripción del error
         */
        public ApiException(int estado, String mensaje) {
            super(mensaje);
            this.estado = estado;
        }

        /**
         * Obtiene el código de estado HTTP asociado al error.
         *
         * @return código HTTP, o 0 si no hubo respuesta del servidor
         */
        public int getEstado() {
            return estado;
        }
    }
}
