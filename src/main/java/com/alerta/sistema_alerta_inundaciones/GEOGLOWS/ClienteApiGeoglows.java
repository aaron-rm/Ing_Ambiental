package com.alerta.sistema_alerta_inundaciones.GEOGLOWS;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * =====================================================================================
 *  CLIENTE DE LA API DE GEOGloWS
 * =====================================================================================
 *
 * Responsabilidad única: hablar con la API pública de GEOGloWS
 * (https://geoglows.ecmwf.int/api/) y devolver los datos ya convertidos a los
 * objetos del modelo definidos en {@link LectorDatosIngresados}.
 *
 * No conoce umbrales, alertas ni nada de negocio: solo consulta y parsea.
 * Si GEOGloWS cambia de versión (p.ej. V2 en data.geoglows.org) solo hay que
 * tocar BASE_URL y las rutas de esta clase.
 * =====================================================================================
 */
@Component
public class ClienteApiGeoglows {

    private static final Logger log = LoggerFactory.getLogger(ClienteApiGeoglows.class);
    private static final String BASE_URL = "https://geoglows.ecmwf.int/api/v2/";
    private static final int MAX_INTENTOS = 3;
    private static final long MAX_RETRY_AFTER_SEGUNDOS = 10L;
    private static final Duration TTL_RETURN_PERIODS = Duration.ofDays(7);
    private static final Duration VENTANA_FALLO_DEFECTO = Duration.ofMinutes(10);
    private static final String MENSAJE_429 =
            "GEOGloWS está limitando las consultas (429). Intenta de nuevo en un minuto.";

    private final String baseUrl;
    private final HttpClient http;
    private final Duration cacheTtl;
    private final Duration esperaBase;
    private final Semaphore semaforo;
    private final RitmoPeticiones ritmo;
    private final Path cacheDir;
    private final Duration ventanaFallo;
    private final Map<String, EntradaCache> cache = new ConcurrentHashMap<>();
    private final Map<String, Instant> fallosRecientes = new ConcurrentHashMap<>();
    private final Map<String, Object> bloqueos = new ConcurrentHashMap<>();

    @Autowired
    public ClienteApiGeoglows(
            @Value("${geoglows.cache-minutos:15}") long cacheMinutos,
            @Value("${geoglows.max-concurrentes:1}") int maxConcurrentes,
            @Value("${geoglows.intervalo-minimo-ms:1500}") long intervaloMinimoMs,
            @Value("${geoglows.cache-dir:${user.home}/.ing-ambiental/geoglows-cache}") String cacheDir) {
        this(
                BASE_URL,
                Duration.ofMinutes(Math.max(1, cacheMinutos)),
                Duration.ofSeconds(5),
                maxConcurrentes,
                intervaloMinimoMs,
                resolverCacheDir(cacheDir),
                VENTANA_FALLO_DEFECTO);
    }

    public ClienteApiGeoglows(long cacheMinutos, int maxConcurrentes) {
        this(
                BASE_URL,
                Duration.ofMinutes(Math.max(1, cacheMinutos)),
                Duration.ofSeconds(5),
                maxConcurrentes,
                1500L,
                null,
                VENTANA_FALLO_DEFECTO);
    }

    public ClienteApiGeoglows(long cacheMinutos) {
        this(cacheMinutos, 1);
    }

    /** Constructor sin argumentos para usos heredados fuera de Spring. */
    public ClienteApiGeoglows() {
        this(15, 1);
    }

    /** Constructor para pruebas con un servidor HTTP local y un TTL explícito. */
    ClienteApiGeoglows(String baseUrl, Duration cacheTtl) {
        this(baseUrl, cacheTtl, Duration.ofSeconds(1));
    }

    /** Constructor para pruebas con TTL y espera base entre reintentos explícitos. */
    ClienteApiGeoglows(String baseUrl, Duration cacheTtl, Duration esperaBase) {
        this(baseUrl, cacheTtl, esperaBase, null);
    }

    /** Constructor para pruebas con caché en disco explícita. */
    ClienteApiGeoglows(String baseUrl, Duration cacheTtl, Duration esperaBase, Path cacheDir) {
        this(baseUrl, cacheTtl, esperaBase, 1, 0L, cacheDir, Duration.ZERO);
    }

    /** Constructor para pruebas con caché en disco y ventana de fallo negativo explícitas. */
    ClienteApiGeoglows(
            String baseUrl,
            Duration cacheTtl,
            Duration esperaBase,
            Path cacheDir,
            Duration ventanaFallo) {
        this(baseUrl, cacheTtl, esperaBase, 1, 0L, cacheDir, ventanaFallo);
    }

    private ClienteApiGeoglows(
            String baseUrl,
            Duration cacheTtl,
            Duration esperaBase,
            int maxConcurrentes,
            long intervaloMinimoMs,
            Path cacheDir,
            Duration ventanaFallo) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .build();
        this.cacheTtl = cacheTtl;
        this.esperaBase = esperaBase;
        this.semaforo = new Semaphore(Math.max(1, maxConcurrentes));
        this.ritmo = new RitmoPeticiones(intervaloMinimoMs);
        this.cacheDir = cacheDir;
        this.ventanaFallo = ventanaFallo != null ? ventanaFallo : Duration.ZERO;
    }

    private static Path resolverCacheDir(String cacheDir) {
        if (cacheDir == null || cacheDir.isBlank()) {
            return null;
        }
        return Path.of(cacheDir.trim());
    }

    /**
     * Llama un endpoint de GEOGloWS y devuelve el JSON ya parseado como Map.
     *
     * Las respuestas se cachean en memoria por endpoint + reachId durante
     * {@code geoglows.cache-minutos}. Si GEOGloWS falla y existe una respuesta
     * anterior, se reutiliza esa respuesta marcando la advertencia en el log:
     * el objetivo es que una caída breve del servicio no deje el panel sin datos.
     */
    private Map<String, Object> get(String path, String query) throws IOException, InterruptedException {
        String clave = path + (query.isEmpty() ? "" : "?" + query);
        Duration ttl = ttlPara(clave);
        EntradaCache entrada = cache.get(clave);
        Instant ahora = Instant.now();

        if (entrada != null && entrada.fechaConsulta.plus(ttl).isAfter(ahora)) {
            log.debug("GEOGloWS: respuesta en caché para {}", clave);
            return entrada.respuesta;
        }

        // Serializa peticiones simultáneas a la misma clave para no repetir el fallo de caché contra GEOGloWS.
        synchronized (bloqueos.computeIfAbsent(clave, k -> new Object())) {
            entrada = cache.get(clave);
            if (entrada == null) {
                entrada = cargarDesdeDisco(clave);
                if (entrada != null) {
                    cache.put(clave, entrada);
                }
            }
            ahora = Instant.now();
            if (entrada != null && entrada.fechaConsulta.plus(ttl).isAfter(ahora)) {
                log.debug("GEOGloWS: respuesta en caché para {}", clave);
                return entrada.respuesta;
            }

            Instant falloReciente = fallosRecientes.get(clave);
            if (falloReciente != null
                    && !ventanaFallo.isZero()
                    && !ventanaFallo.isNegative()
                    && falloReciente.isAfter(ahora.minus(ventanaFallo))) {
                if (entrada != null) {
                    log.debug("GEOGloWS falló hace poco para {}. Se reutiliza la respuesta cacheada de {}.",
                            clave, entrada.fechaConsulta);
                    return entrada.respuesta;
                }
                throw new IOException("GEOGloWS no respondió hace poco para " + clave + "; se reintentará más tarde.");
            }

            try {
                EntradaCache nueva = consultar(path, query);
                fallosRecientes.remove(clave);
                guardarEnDisco(clave, nueva.cuerpoCrudo);
                cache.put(clave, new EntradaCache(nueva.fechaConsulta, nueva.respuesta, null));
                return nueva.respuesta;
            } catch (IOException e) {
                fallosRecientes.put(clave, Instant.now());
                if (entrada != null) {
                    log.warn("GEOGloWS falló para {} ({}). Se reutiliza la respuesta cacheada de {}.",
                            clave, e.getMessage(), entrada.fechaConsulta);
                    return entrada.respuesta;
                }
                throw e;
            }
        }
    }

    private Duration ttlPara(String clave) {
        return clave.startsWith("returnperiods") ? TTL_RETURN_PERIODS : cacheTtl;
    }

    private EntradaCache cargarDesdeDisco(String clave) {
        if (cacheDir == null) {
            return null;
        }
        Path archivo = archivoCache(clave);
        if (!Files.isRegularFile(archivo)) {
            return null;
        }
        try {
            Instant fecha = Files.getLastModifiedTime(archivo).toInstant();
            String contenido = Files.readString(archivo, StandardCharsets.UTF_8);
            if (contenido.isBlank()) {
                return null;
            }
            return new EntradaCache(fecha, parsearMapa(contenido), null);
        } catch (IOException | RuntimeException e) {
            log.warn("No se pudo leer caché de disco {}: {}", archivo, e.getMessage());
            return null;
        }
    }

    private void guardarEnDisco(String clave, String cuerpoCrudo) {
        if (cacheDir == null || cuerpoCrudo == null) {
            return;
        }
        Path archivo = archivoCache(clave);
        Path temporal = null;
        try {
            Files.createDirectories(cacheDir);
            temporal = Files.createTempFile(cacheDir, archivo.getFileName().toString(), ".tmp");
            Files.writeString(temporal, cuerpoCrudo, StandardCharsets.UTF_8);
            try {
                Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            log.warn("No se pudo guardar caché de disco {}: {}", archivo, e.getMessage());
            if (temporal != null) {
                try {
                    Files.deleteIfExists(temporal);
                } catch (IOException ignored) {
                    // Ignorado al limpiar temporal tras fallo de E/S.
                }
            }
        }
    }

    private Path archivoCache(String clave) {
        String nombre = clave.replaceAll("[^A-Za-z0-9._-]", "_") + ".json";
        return cacheDir.resolve(nombre);
    }

    private EntradaCache consultar(String path, String query)
            throws IOException, InterruptedException {
        String url = baseUrl + path + (query.isEmpty() ? "" : "?" + query);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(4))
                .GET()
                .build();

        for (int intento = 1; intento <= MAX_INTENTOS; intento++) {
            HttpResponse<String> resp;
            semaforo.acquire();
            try {
                ritmo.esperarTurno();
                resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            } finally {
                semaforo.release();
            }

            int status = resp.statusCode();
            if (status == 200) {
                String cuerpo = resp.body();
                return new EntradaCache(Instant.now(), parsearMapa(cuerpo), cuerpo);
            }

            if (esReintentable(status) && intento < MAX_INTENTOS) {
                Duration espera = calcularEspera(resp, intento);
                log.warn("GEOGloWS respondió {} para {} (intento {}/{}). Reintentando en {} ms.",
                        status, url, intento, MAX_INTENTOS, espera.toMillis());
                long esperaMs = espera.toMillis();
                if (esperaMs > 0) {
                    Thread.sleep(esperaMs);
                }
                continue;
            }

            if (status == 429) {
                throw new IOException(MENSAJE_429);
            }
            throw new IOException("GEOGloWS respondió " + status + " para " + url);
        }

        throw new IOException("GEOGloWS falló para " + url);
    }

    private static boolean esReintentable(int status) {
        return status == 429 || status == 502 || status == 503 || status == 504;
    }

    private Duration calcularEspera(HttpResponse<String> resp, int intento) {
        String retryAfter = resp.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null && !retryAfter.isBlank()) {
            try {
                long segundos = Long.parseLong(retryAfter.trim());
                if (segundos >= 0) {
                    return Duration.ofSeconds(Math.min(MAX_RETRY_AFTER_SEGUNDOS, segundos));
                }
            } catch (NumberFormatException ignored) {
                // Cabecera no numérica: se usa backoff exponencial con esperaBase.
            }
        }
        return esperaBase.multipliedBy(1L << (intento - 1));
    }

    /**
     * Pronóstico estadístico (min/25%/media/75%/max) del ensemble para un tramo.
     *
     * IMPORTANTE (API v2, verificado en vivo contra
     * {@code /api/v2/forecaststats/{river_id}?format=json}):
     *   - Ya NO viene envuelto en "time_series": el JSON trae "datetime" y las
     *     series ("flow_avg", "flow_25p", "flow_75p", "flow_min", "flow_max",
     *     "flow_med", "high_res", ...) directamente en el objeto raíz, junto a
     *     un objeto "metadata" (river_id, gen_date, start_date, end_date, units).
     *   - Las series NO tienen todas la misma cantidad de puntos "reales": los
     *     pasos horarios que no corresponden al intervalo de 3h vienen como
     *     string vacío "" en vez de un número. Hay que descartarlos (no tratarlos
     *     como 0 ni dejarlos como NaN, porque NaN contamina cálculos de máximo).
     */
    @SuppressWarnings("unchecked")
    public LectorDatosIngresados.PronosticoCaudal obtenerPronostico(long reachId)
            throws IOException, InterruptedException {
        Map<String, Object> raw = get("forecaststats/" + reachId, "format=json");
        // Compatibilidad: si en algún momento GEOGloWS vuelve a envolver la
        // respuesta en "time_series", lo seguimos soportando.
        Object ts = raw.get("time_series");
        Map<String, Object> serie = (ts instanceof Map) ? (Map<String, Object>) ts : raw;

        LectorDatosIngresados.PronosticoCaudal pc = new LectorDatosIngresados.PronosticoCaudal();
        pc.reachId = reachId;
        pc.fechaEmision = Instant.now();

        Object metaObj = raw.get("metadata");
        if (metaObj instanceof Map) {
            Object genDate = ((Map<String, Object>) metaObj).get("gen_date");
            if (genDate != null) {
                pc.fechaEmision = parseFecha(String.valueOf(genDate));
            }
        }

        List<Object> fechas = asList(serie.get("datetime"));
        for (String clave : serie.keySet()) {
            if ("datetime".equals(clave) || "metadata".equals(clave)) continue;
            List<Object> valores = asList(serie.get(clave));
            if (fechas == null || valores == null || fechas.size() != valores.size()) continue;

            List<LectorDatosIngresados.SerieTemporalPunto> puntos = new ArrayList<>();
            for (int idx = 0; idx < fechas.size(); idx++) {
                Object crudo = valores.get(idx);
                if (esVacio(crudo)) continue; // paso sin dato ("") -> se omite, no se agrega como 0/NaN
                LectorDatosIngresados.SerieTemporalPunto p = new LectorDatosIngresados.SerieTemporalPunto();
                p.fecha = parseFecha(String.valueOf(fechas.get(idx)));
                p.valor = asDouble(crudo);
                puntos.add(p);
            }
            String claveMin = clave.toLowerCase();
            if (claveMin.contains("avg") || claveMin.contains("mean")) {
                pc.mediaSerie = puntos;
            } else {
                pc.percentiles.put(clave, puntos);
            }
        }
        if (!pc.mediaSerie.isEmpty()) {
            Instant inicio = pc.mediaSerie.get(0).fecha;
            Instant fin = pc.mediaSerie.get(pc.mediaSerie.size() - 1).fecha;
            pc.horizonteHoras = (int) Duration.between(inicio, fin).toHours();
        }
        return pc;
    }

    /** Periodos de retorno (años -> caudal en m3/s) calculados por GEOGloWS con su histórico. */
    @SuppressWarnings("unchecked")
    public Map<Integer, Double> obtenerPeriodosRetorno(long reachId) throws IOException, InterruptedException {
        Map<String, Object> raw = get("returnperiods/" + reachId, "format=json");
        Object rp = raw.containsKey("return_periods") ? raw.get("return_periods") : raw;
        Map<String, Object> mapa = (rp instanceof Map) ? (Map<String, Object>) rp : raw;

        Map<Integer, Double> resultado = new TreeMap<>();
        for (Map.Entry<String, Object> e : mapa.entrySet()) {
            String soloDigitos = e.getKey().replaceAll("[^0-9]", "");
            if (soloDigitos.isEmpty()) continue;
            try {
                resultado.put(Integer.parseInt(soloDigitos), asDouble(e.getValue()));
            } catch (NumberFormatException ignored) {
                // clave no numérica (p.ej. "reach_id"): se ignora
            }
        }
        return resultado;
    }

    /**
     * Deriva umbrales de vigilancia/alerta/emergencia a partir de los periodos de
     * retorno de GEOGloWS (convención habitual: 2/5/25 años). AJUSTAR con aforos
     * locales, registros de desbordamiento o curvas caudal-nivel cuando existan.
     */
    public LectorDatosIngresados.UmbralAlerta obtenerUmbrales(long reachId)
            throws IOException, InterruptedException {
        Map<Integer, Double> periodos;
        boolean usandoReferencia = false;
        try {
            periodos = obtenerPeriodosRetorno(reachId);
        } catch (IOException e) {
            // Si la API remota de GEOGloWS falla temporalmente en returnperiods (ej: error interno de variable o dataset),
            // usamos umbrales predeterminados de referencia para no bloquear el flujo de evaluación del tramo.
            periodos = new TreeMap<>();
            periodos.put(2, 15.0);
            periodos.put(5, 30.0);
            periodos.put(25, 60.0);
            usandoReferencia = true;
        }
        LectorDatosIngresados.UmbralAlerta u = new LectorDatosIngresados.UmbralAlerta();
        u.reachId = reachId;
        u.caudalVigilancia = periodos.getOrDefault(2, 15.0);
        u.caudalAlerta = periodos.getOrDefault(5, 30.0);
        u.caudalEmergencia = periodos.getOrDefault(25, 60.0);
        u.fuenteCalibracion = usandoReferencia
                ? "Configuración de referencia (demostración, no oficial)"
                : "GEOGloWS - periodos de retorno";
        u.fechaCalibracion = LocalDate.now();
        return u;
    }

    /**
     * Simulación retrospectiva de caudal (ERA5, desde 1940) para un tramo.
     *
     * En la API v1 esto se llamaba "historicsimulation". Ese endpoint ya no
     * existe en v2: se reemplazó por retrospectivedaily/-monthly/-hourly.
     * Usamos la variante diaria, que es la que corresponde 1:1 al uso anterior.
     */
    @SuppressWarnings("unchecked")
    public List<LectorDatosIngresados.SerieTemporalPunto> obtenerHistorico(long reachId)
            throws IOException, InterruptedException {
        Map<String, Object> raw = get("retrospectivedaily/" + reachId, "format=json");
        Object ts = raw.get("time_series");
        Map<String, Object> serie = (ts instanceof Map) ? (Map<String, Object>) ts : raw;

        List<Object> fechas = asList(serie.get("datetime"));
        List<Object> valores = null;
        for (String k : serie.keySet()) {
            if (!"datetime".equals(k)) {
                valores = asList(serie.get(k));
                break;
            }
        }
        List<LectorDatosIngresados.SerieTemporalPunto> resultado = new ArrayList<>();
        if (fechas != null && valores != null) {
            int n = Math.min(fechas.size(), valores.size());
            for (int idx = 0; idx < n; idx++) {
                Object crudo = valores.get(idx);
                if (esVacio(crudo)) continue;
                LectorDatosIngresados.SerieTemporalPunto p = new LectorDatosIngresados.SerieTemporalPunto();
                p.fecha = parseFecha(String.valueOf(fechas.get(idx)));
                p.valor = asDouble(crudo);
                resultado.add(p);
            }
        }
        return resultado;
    }

    /**
     * Busca el reach_id/LINKNO más cercano a una coordenada (endpoint v2 "getriverid").
     * Útil para asociar automáticamente estaciones/observaciones/elementos expuestos
     * (que llegan con lat/lon) a un tramo de GEOGloWS.
     */
    @SuppressWarnings("unchecked")
    public Long buscarReachIdCercano(double lat, double lon) throws IOException, InterruptedException {
        Map<String, Object> raw = get("getriverid", "lat=" + lat + "&lon=" + lon + "&format=json");
        for (String clave : new String[]{"river_id", "reach_id", "LINKNO", "linkno", "id"}) {
            Object valor = raw.get(clave);
            if (valor != null) {
                try {
                    return Long.parseLong(String.valueOf(valor));
                } catch (NumberFormatException ignored) {
                    // sigue probando otras claves
                }
            }
        }
        return null;
    }

    /**
     * Tramos de una región que GEOGloWS reporta actualmente por encima de algún periodo de retorno.
     *
     * NOTA: este endpoint ("forecastwarnings") pertenece a la API v1. No aparece en el
     * listado actual de la documentación interactiva de v2 que se usó para revisar este
     * cliente, así que su disponibilidad no está garantizada; si GEOGloWS lo retira,
     * este método empezará a fallar con IOException (respuesta != 200) y conviene
     * quitarlo o reemplazarlo (por ejemplo, iterando obtenerPronostico + obtenerUmbrales
     * por tramo, que es exactamente lo que ya hace GeneradorAlertaFinal).
     */
    @SuppressWarnings("unchecked")
    public List<Long> obtenerTramosEnAlerta(String region) throws IOException, InterruptedException {
        Map<String, Object> raw = get("forecastwarnings/", "region=" + region + "&format=json");
        Object contenido = raw.containsKey("warnings") ? raw.get("warnings") : raw.get("data");
        List<Object> lista = asList(contenido);
        List<Long> ids = new ArrayList<>();
        if (lista != null) {
            for (Object o : lista) {
                if (o instanceof Map) {
                    Object rid = ((Map<String, Object>) o).get("reach_id");
                    if (rid != null) ids.add(Long.parseLong(String.valueOf(rid)));
                }
            }
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object o) {
        return (o instanceof List) ? (List<Object>) o : null;
    }

    /** true si el valor representa "sin dato" (null o string vacío/blanco, como en las series v2). */
    private static boolean esVacio(Object o) {
        return o == null || (o instanceof String && ((String) o).isBlank());
    }

    private static double asDouble(Object o) {
        if (o == null) return Double.NaN;
        if (o instanceof Number) return ((Number) o).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static Instant parseFecha(String texto) {
        if (texto == null || texto.isBlank()) return Instant.now();
        try {
            if (texto.contains("+") || texto.endsWith("Z")) {
                return java.time.OffsetDateTime.parse(texto.replace(' ', 'T')).toInstant();
            }
            return LocalDateTime.parse(texto.replace(' ', 'T')).atZone(ZoneOffset.UTC).toInstant();
        } catch (Exception e) {
            try {
                return Instant.parse(texto);
            } catch (Exception e2) {
                return Instant.now();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> parsearMapa(String cuerpo) {
        Object parsed = Json.parse(cuerpo);
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        Map<String, Object> envoltorio = new LinkedHashMap<>();
        envoltorio.put("data", parsed);
        return envoltorio;
    }

    private record EntradaCache(Instant fechaConsulta, Map<String, Object> respuesta, String cuerpoCrudo) {
    }

    private static final class RitmoPeticiones {
        private final long intervaloMinimoNanos;
        private long ultimaPeticionNanos;
        private boolean iniciado;

        private RitmoPeticiones(long intervaloMinimoMs) {
            this.intervaloMinimoNanos = Math.max(0L, intervaloMinimoMs) * 1_000_000L;
        }

        synchronized void esperarTurno() throws InterruptedException {
            if (intervaloMinimoNanos <= 0L) {
                return;
            }
            if (iniciado) {
                long restanteNanos = intervaloMinimoNanos - (System.nanoTime() - ultimaPeticionNanos);
                if (restanteNanos > 0L) {
                    Thread.sleep(restanteNanos / 1_000_000L, (int) (restanteNanos % 1_000_000L));
                }
            }
            ultimaPeticionNanos = System.nanoTime();
            iniciado = true;
        }
    }

    /**
     * Parser JSON minimalista (sin dependencias externas), usado solo por este cliente
     * para interpretar las respuestas de la API.
     */
    static final class Json {
        private final String s;
        private int i;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String texto) {
            Json p = new Json(texto);
            p.skipWs();
            return p.parseValue();
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object parseValue() {
            skipWs();
            char c = s.charAt(i);
            if (c == '{') return parseObject();
            if (c == '[') return parseArray();
            if (c == '"') return parseString();
            if (c == 't') { i += 4; return Boolean.TRUE; }
            if (c == 'f') { i += 5; return Boolean.FALSE; }
            if (c == 'n') { i += 4; return null; }
            return parseNumber();
        }

        private Map<String, Object> parseObject() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // '{'
            skipWs();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                skipWs();
                String clave = parseString();
                skipWs();
                i++; // ':'
                Object valor = parseValue();
                m.put(clave, valor);
                skipWs();
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
            }
            return m;
        }

        private List<Object> parseArray() {
            List<Object> l = new ArrayList<>();
            i++; // '['
            skipWs();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(parseValue());
                skipWs();
                char c = s.charAt(i);
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
            }
            return l;
        }

        private String parseString() {
            StringBuilder sb = new StringBuilder();
            i++; // '"'
            while (s.charAt(i) != '"') {
                char c = s.charAt(i);
                if (c == '\\') {
                    i++;
                    char e = s.charAt(i);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case '"': sb.append('"'); break;
                        case '\\': sb.append('\\'); break;
                        case '/': sb.append('/'); break;
                        case 'u':
                            String hex = s.substring(i + 1, i + 5);
                            sb.append((char) Integer.parseInt(hex, 16));
                            i += 4;
                            break;
                        default: sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
                i++;
            }
            i++; // '"' de cierre
            return sb.toString();
        }

        private Object parseNumber() {
            int inicio = i;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (Character.isDigit(c) || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    i++;
                } else {
                    break;
                }
            }
            String num = s.substring(inicio, i);
            if (num.contains(".") || num.toLowerCase().contains("e")) {
                return Double.parseDouble(num);
            }
            try {
                return Long.parseLong(num);
            } catch (NumberFormatException ex) {
                return Double.parseDouble(num);
            }
        }
    }
}
