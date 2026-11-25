package com.alerta.sistema_alerta_inundaciones.GEOGLOWS;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
        ClienteApiGeoglows cliente = cliente(Duration.ofMinutes(15), Duration.ofMillis(10));

        assertThrows(IOException.class, () -> cliente.obtenerPronostico(710039498L));
        assertEquals(3, solicitudes.get());
    }

    @Test
    void reintentaCuandoGeoglowsResponde429() throws Exception {
        servidor.createContext("/api/v2/forecaststats/710039498", intercambio -> {
            int numero = solicitudes.incrementAndGet();
            if (numero == 1) {
                responder(intercambio, 429, "{\"error\":\"demasiadas peticiones\"}");
            } else {
                responder(intercambio, 200, pronostico(18.5));
            }
        });
        ClienteApiGeoglows cliente = cliente(Duration.ofMinutes(15), Duration.ofMillis(10));

        var resultado = cliente.obtenerPronostico(710039498L);

        assertEquals(2, solicitudes.get());
        assertEquals(18.5, resultado.mediaSerie.get(0).valor);
    }

    @Test
    void deduplicaPeticionesSimultaneasParaLaMismaUrl() throws Exception {
        servidor.createContext("/api/v2/forecaststats/710039498", intercambio -> {
            solicitudes.incrementAndGet();
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            responder(intercambio, 200, pronostico(15.0));
        });
        ClienteApiGeoglows cliente = cliente(Duration.ofMinutes(15), Duration.ofMillis(10));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrera = new CyclicBarrier(2);
        try {
            Future<LectorDatosIngresados.PronosticoCaudal> f1 = pool.submit(() -> {
                barrera.await(5, TimeUnit.SECONDS);
                return cliente.obtenerPronostico(710039498L);
            });
            Future<LectorDatosIngresados.PronosticoCaudal> f2 = pool.submit(() -> {
                barrera.await(5, TimeUnit.SECONDS);
                return cliente.obtenerPronostico(710039498L);
            });

            assertEquals(15.0, f1.get(5, TimeUnit.SECONDS).mediaSerie.get(0).valor);
            assertEquals(15.0, f2.get(5, TimeUnit.SECONDS).mediaSerie.get(0).valor);
            assertEquals(1, solicitudes.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void reutilizaLaCacheEnDiscoEntreInstancias(@TempDir Path dirCache) throws Exception {
        servidor.createContext("/api/v2/forecaststats/710039498", intercambio -> {
            solicitudes.incrementAndGet();
            responder(intercambio, 200, pronostico(14.25));
        });
        ClienteApiGeoglows clienteA = cliente(Duration.ofMinutes(15), Duration.ofMillis(10), dirCache);
        var primera = clienteA.obtenerPronostico(710039498L);

        ClienteApiGeoglows clienteB = cliente(Duration.ofMinutes(15), Duration.ofMillis(10), dirCache);
        var segunda = clienteB.obtenerPronostico(710039498L);

        assertEquals(1, solicitudes.get());
        assertEquals(14.25, primera.mediaSerie.get(0).valor);
        assertEquals(14.25, segunda.mediaSerie.get(0).valor);
        assertEquals(primera.fechaEmision, segunda.fechaEmision);
    }

    @Test
    void reutilizaLaCacheEnDiscoCuandoGeoglowsFalla(@TempDir Path dirCache) throws Exception {
        servidor.createContext("/api/v2/forecaststats/710039498", intercambio -> {
            int numero = solicitudes.incrementAndGet();
            if (numero == 1) {
                responder(intercambio, 200, pronostico(21.5));
            } else {
                responder(intercambio, 500, "{\"error\":\"fallo temporal\"}");
            }
        });
        ClienteApiGeoglows clienteA = cliente(Duration.ZERO, Duration.ofMillis(10), dirCache);
        var primera = clienteA.obtenerPronostico(710039498L);

        ClienteApiGeoglows clienteB = cliente(Duration.ZERO, Duration.ofMillis(10), dirCache);
        var segunda = clienteB.obtenerPronostico(710039498L);

        assertEquals(2, solicitudes.get());
        assertEquals(21.5, primera.mediaSerie.get(0).valor);
        assertEquals(21.5, segunda.mediaSerie.get(0).valor);
    }

    private ClienteApiGeoglows cliente(Duration ttl) {
        return cliente(ttl, Duration.ofMillis(10));
    }

    private ClienteApiGeoglows cliente(Duration ttl, Duration esperaBase) {
        return new ClienteApiGeoglows(
                "http://localhost:" + servidor.getAddress().getPort() + "/api/v2/",
                ttl,
                esperaBase);
    }

    private ClienteApiGeoglows cliente(Duration ttl, Duration esperaBase, Path cacheDir) {
        return new ClienteApiGeoglows(
                "http://localhost:" + servidor.getAddress().getPort() + "/api/v2/",
                ttl,
                esperaBase,
                cacheDir);
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
