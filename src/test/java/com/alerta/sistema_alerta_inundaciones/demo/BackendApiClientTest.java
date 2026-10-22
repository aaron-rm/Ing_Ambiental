package com.alerta.sistema_alerta_inundaciones.demo;

import com.alerta.sistema_alerta_inundaciones.push.ResultadoDemoPush;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BackendApiClientTest {

    private HttpServer servidor;

    @BeforeEach
    void preparar() throws IOException {
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
    void listaRiosDesdeLaApiPublica() {
        servidor.createContext("/api/rios", intercambio -> responder(intercambio, 200,
                "[{\"id\":1,\"nombre\":\"Rio Pacora\",\"geoglowsId\":710039498,"
                        + "\"latitud\":9.08,\"longitud\":-79.28}]"));

        BackendApiClient cliente = cliente("");

        List<BackendApiClient.RioApi> rios = cliente.listarRios();

        assertEquals(1, rios.size());
        assertEquals(710039498L, rios.get(0).geoglowsId());
        assertEquals("Rio Pacora", rios.get(0).nombre());
    }

    @Test
    void enviaTokenAdministrativoEnEndpointProtegido() {
        AtomicReference<String> tokenRecibido = new AtomicReference<>();
        servidor.createContext("/api/alertas/suscripciones/710039498", intercambio -> {
            tokenRecibido.set(intercambio.getRequestHeaders().getFirst("X-Admin-Token"));
            responder(intercambio, 200, "[]");
        });

        BackendApiClient cliente = cliente("token-secreto");
        List<BackendApiClient.SuscripcionApi> suscripciones =
                cliente.listarSuscripciones(710039498L);

        assertTrue(suscripciones.isEmpty());
        assertEquals("token-secreto", tokenRecibido.get());
    }

    @Test
    void convertirErrorHttpEnApiExceptionConCodigo() {
        servidor.createContext("/api/rios", intercambio ->
                responder(intercambio, 403, "Acceso restringido."));

        BackendApiClient cliente = cliente("");

        BackendApiClient.ApiException error = assertThrows(
                BackendApiClient.ApiException.class, cliente::listarRios);
        assertEquals(403, error.getEstado());
        assertTrue(error.getMessage().contains("Acceso restringido"));
    }

    @Test
    void enviaDemostracionPushPorElEndpointProtegido() {
        AtomicReference<String> tokenRecibido = new AtomicReference<>();
        AtomicReference<String> metodoRecibido = new AtomicReference<>();
        servidor.createContext("/api/push/demo", intercambio -> {
            tokenRecibido.set(intercambio.getRequestHeaders().getFirst("X-Admin-Token"));
            metodoRecibido.set(intercambio.getRequestMethod());
            responder(intercambio, 200,
                    "{\"dispositivos\":2,\"enviados\":2,\"fallidos\":0}");
        });

        BackendApiClient cliente = cliente("token-secreto");
        ResultadoDemoPush resultado = cliente.enviarDemoPush();

        assertNotNull(resultado);
        assertEquals(2, resultado.dispositivos());
        assertEquals(2, resultado.enviados());
        assertEquals(0, resultado.fallidos());
        assertEquals("token-secreto", tokenRecibido.get());
        assertEquals("POST", metodoRecibido.get());
    }

    private BackendApiClient cliente(String tokenAdmin) {
        return new BackendApiClient(
                "http://localhost:" + servidor.getAddress().getPort(), tokenAdmin);
    }

    private void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        intercambio.getResponseBody().write(bytes);
        intercambio.close();
    }
}
