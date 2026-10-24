package com.alerta.sistema_alerta_inundaciones.GEOGLOWS;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClienteApiGeoglowsTest {

    private HttpServer servidor;
    private AtomicInteger solicitudes;

    @BeforeEach
    void preparar() throws IOException {
        solicitudes = new AtomicInteger();
        servidor = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        servidor.start();
    }

    @AfterEach
    void detener() {
        if (servidor != null) {
            servidor.stop(0);
        }
    }

    @Test
    void reutilizaLaRespuestaDentroDelTtl() throws Exception {
        servidor.createContext("/api/v2/forecaststats/710039498", intercambio -> {
            solicitudes.incrementAndGet();
            responder(intercambio, 200, pronostico(12.5));
        });
        ClienteApiGeoglows cliente = cliente(Duration.ofMinutes(15));

        var primera = cliente.obtenerPronostico(710039498L);
        var segunda = cliente.obtenerPronostico(710039498L);

        assertEquals(1, solicitudes.get());
        assertEquals(12.5, primera.mediaSerie.get(0).valor);
        assertEquals(primera.mediaSerie.size(), segunda.mediaSerie.size());
        assertEquals(primera.fechaEmision, segunda.fechaEmision);
    }

    @Test
    void cacheaPorTramoSinMezclarDatos() throws Exception {
        servidor.createContext("/api/v2/forecaststats/710039498", intercambio -> {
            solicitudes.incrementAndGet();
            responder(intercambio, 200, pronostico(11.0));
        });
        servidor.createContext("/api/v2/forecaststats/710037516", intercambio -> {
            solicitudes.incrementAndGet();
            responder(intercambio, 200, pronostico(22.0));
        });
        ClienteApiGeoglows cliente = cliente(Duration.ofMinutes(15));

        assertEquals(11.0, cliente.obtenerPronostico(710039498L).mediaSerie.get(0).valor);
        assertEquals(22.0, cliente.obtenerPronostico(710037516L).mediaSerie.get(0).valor);
        assertEquals(11.0, cliente.obtenerPronostico(710039498L).mediaSerie.get(0).valor);
        assertEquals(22.0, cliente.obtenerPronostico(710037516L).mediaSerie.get(0).valor);

        assertEquals(2, solicitudes.get());
    }

    @Test
    void reutilizaLaCacheAnteriorCuandoGeoglowsFalla() throws Exception {
        servidor.createContext("/api/v2/forecaststats/710039498", intercambio -> {
            int numero = solicitudes.incrementAndGet();
            if (numero == 1) {
                responder(intercambio, 200, pronostico(20.0));
            } else {
                responder(intercambio, 500, "{\"error\":\"fallo temporal\"}");
            }
        });
        ClienteApiGeoglows cliente = cliente(Duration.ZERO);

        var primera = cliente.obtenerPronostico(710039498L);
        var segunda = cliente.obtenerPronostico(710039498L);

        assertEquals(2, solicitudes.get());
        assertEquals(20.0, primera.mediaSerie.get(0).valor);
        assertEquals(20.0, segunda.mediaSerie.get(0).valor);
    }

    @Test
    void propagaElErrorCuandoNoHayCachePrevia() {
        servidor.createContext("/api/v2/forecaststats/710039498", intercambio -> {
            solicitudes.incrementAndGet();
            responder(intercambio, 503, "{\"error\":\"sin servicio\"}");
        });
        ClienteApiGeoglows cliente = cliente(Duration.ofMinutes(15));

        assertThrows(IOException.class, () -> cliente.obtenerPronostico(710039498L));
        assertEquals(1, solicitudes.get());
    }

    private ClienteApiGeoglows cliente(Duration ttl) {
        return new ClienteApiGeoglows(
                "http://localhost:" + servidor.getAddress().getPort() + "/api/v2/", ttl);
    }

    private String pronostico(double caudal) {
        return "{\"datetime\":[\"2026-10-23T00:00:00Z\",\"2026-10-23T03:00:00Z\"],"
                + "\"flow_avg\":[" + caudal + "," + (caudal + 1.0) + "],"
                + "\"metadata\":{\"gen_date\":\"2026-10-23T00:00:00Z\"}}";
    }

    private void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        intercambio.getResponseBody().write(bytes);
        intercambio.close();
    }
}
