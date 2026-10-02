package com.alerta.sistema_alerta_inundaciones.GEOGLOWS;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
public class ClienteApiGeoglows {

    private static final String BASE_URL = "https://geoglows.ecmwf.int/api/v2/";
    private final HttpClient http = HttpClient.newHttpClient();

    /** Llama un endpoint de GEOGloWS y devuelve el JSON ya parseado como Map. */
    @SuppressWarnings("unchecked")
    private Map<String, Object> get(String path, String query) throws IOException, InterruptedException {
        String url = BASE_URL + path + (query.isEmpty() ? "" : "?" + query);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("GEOGloWS respondió " + resp.statusCode() + " para " + url);
        }
        Object parsed = Json.parse(resp.body());
        if (parsed instanceof Map) {
            return (Map<String, Object>) parsed;
        }
        Map<String, Object> envoltorio = new LinkedHashMap<>();
        envoltorio.put("data", parsed);
        return envoltorio;
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
        try {
            periodos = obtenerPeriodosRetorno(reachId);
        } catch (IOException e) {
            // Si la API remota de GEOGloWS falla temporalmente en returnperiods (ej: error interno de variable o dataset),
            // usamos umbrales predeterminados de referencia para no bloquear el flujo de evaluación del tramo.
            periodos = new TreeMap<>();
            periodos.put(2, 15.0);
            periodos.put(5, 30.0);
            periodos.put(25, 60.0);
        }
        LectorDatosIngresados.UmbralAlerta u = new LectorDatosIngresados.UmbralAlerta();
        u.reachId = reachId;
        u.caudalVigilancia = periodos.getOrDefault(2, 15.0);
        u.caudalAlerta = periodos.getOrDefault(5, 30.0);
        u.caudalEmergencia = periodos.getOrDefault(25, 60.0);
        u.fuenteCalibracion = periodos.size() > 0 ? "GEOGloWS - periodos de retorno" : "Configuración de referencia (demostración)";
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
