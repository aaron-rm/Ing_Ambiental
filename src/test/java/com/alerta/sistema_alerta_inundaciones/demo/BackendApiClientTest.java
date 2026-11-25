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
    void listaRiosDesdeLaApiPublicaConElNuevoContrato() {
        servidor.createContext("/api/rios", intercambio -> responder(intercambio, 200,
                "[{\"rioId\":1,\"nombre\":\"Río Abajo\",\"reachId\":710039498,"
                        + "\"latitud\":9.0,\"longitud\":-79.48,\"tieneGeometria\":true}]"));

        BackendApiClient cliente = cliente();

        List<BackendApiClient.RioApi> rios = cliente.listarRios();

        assertEquals(1, rios.size());
        assertEquals(710039498L, rios.get(0).reachId());
        assertEquals(1L, rios.get(0).rioId());
        assertEquals("Río Abajo", rios.get(0).nombre());
        assertTrue(rios.get(0).tieneGeometria());
    }

    @Test
    void elLoginGuardaElTokenYLoEnviaComoBearer() {
        AtomicReference<String> autorizacion = new AtomicReference<>();
        servidor.createContext("/api/auth/login", intercambio -> {
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            responder(intercambio, 200,
                    "{\"token\":\"jwt-de-prueba\",\"usuario\":{\"id\":7,\"identificador\":\"juan\",\"rol\":\"USER\"}}");
        });
        servidor.createContext("/api/usuarios/me/rios", intercambio -> {
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            responder(intercambio, 200, "[]");
        });

        BackendApiClient cliente = cliente();
        BackendApiClient.SesionApi sesion = cliente.iniciarSesion("juan", "clave-segura-123");
        List<BackendApiClient.RioSeguidoApi> seguidos = cliente.listarRiosSeguidos();

        assertEquals("juan", sesion.usuario().identificador());
        assertEquals("USER", sesion.usuario().rol());
        assertTrue(seguidos.isEmpty());
        assertEquals("Bearer jwt-de-prueba", autorizacion.get());
        assertTrue(cliente.tieneSesion());
    }

    @Test
    void seguirYDejarDeSeguirUnRioUsanElRioId() {
        AtomicReference<String> ruta = new AtomicReference<>();
        AtomicReference<String> metodo = new AtomicReference<>();
        servidor.createContext("/api/usuarios/me/rios/4", intercambio -> {
            ruta.set(intercambio.getRequestURI().getPath());
            metodo.set(intercambio.getRequestMethod());
            responder(intercambio, 200,
                    "{\"rioId\":4,\"seguido\":true,\"mensaje\":\"Río agregado a tus alertas.\"}");
        });

        BackendApiClient cliente = cliente();
        BackendApiClient.SeleccionApi respuesta = cliente.seguirRio(4L);

        assertTrue(respuesta.seguido());
        assertEquals("/api/usuarios/me/rios/4", ruta.get());
        assertEquals("POST", metodo.get());
    }

    @Test
    void convertirErrorHttpEnApiExceptionConCodigo() {
        servidor.createContext("/api/rios", intercambio ->
                responder(intercambio, 403, "No tienes permiso."));

        BackendApiClient cliente = cliente();

        BackendApiClient.ApiException error = assertThrows(
                BackendApiClient.ApiException.class, cliente::listarRios);
        assertEquals(403, error.getEstado());
        assertTrue(error.getMessage().contains("No tienes permiso"));
    }

    @Test
    void simularLeeElResultadoDeLaSimulacionYElResumenPush() {
        AtomicReference<String> consulta = new AtomicReference<>();
        servidor.createContext("/api/alertas/simular", intercambio -> {
            consulta.set(intercambio.getRequestURI().getQuery());
            responder(intercambio, 200,
                    "{\"alerta\":{\"reachId\":710039498,\"nombreRio\":\"Río Abajo\","
                            + "\"nivelAlerta\":\"EMERGENCIA\",\"simulacion\":true},"
                            + "\"dispositivos\":2,\"enviados\":2,\"fallidos\":0}");
        });

        BackendApiClient cliente = cliente();
        BackendApiClient.ResultadoSimulacionApi resultado = cliente.simular(710039498L, 75.0);

        assertEquals(710039498L, resultado.alerta().getReachId());
        assertEquals("EMERGENCIA", resultado.alerta().getNivelAlerta());
        assertEquals(2, resultado.dispositivos());
        assertEquals(2, resultado.enviados());
        assertTrue(consulta.get().contains("reachId=710039498"));
    }

    @Test
    void enviarDemoPushUsaElEndpointDeAdministracionConBearer() {
        AtomicReference<String> autorizacion = new AtomicReference<>();
        AtomicReference<String> metodo = new AtomicReference<>();
        servidor.createContext("/api/admin/push/demo", intercambio -> {
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            metodo.set(intercambio.getRequestMethod());
            responder(intercambio, 200,
                    "{\"dispositivos\":2,\"enviados\":2,\"fallidos\":0}");
        });
        servidor.createContext("/api/auth/login", intercambio -> responder(intercambio, 200,
                "{\"token\":\"jwt-admin\",\"usuario\":{\"id\":1,\"identificador\":\"admin\",\"rol\":\"ADMIN\"}}"));

        BackendApiClient cliente = cliente();
        cliente.iniciarSesion("admin", "clave-segura-123");
        ResultadoDemoPush resultado = cliente.enviarDemoPush();

        assertNotNull(resultado);
        assertEquals(2, resultado.dispositivos());
        assertEquals(2, resultado.enviados());
        assertEquals(0, resultado.fallidos());
        assertEquals("Bearer jwt-admin", autorizacion.get());
        assertEquals("POST", metodo.get());
    }

    private BackendApiClient cliente() {
        return new BackendApiClient("http://localhost:" + servidor.getAddress().getPort());
    }

    private void responder(HttpExchange intercambio, int estado, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().set("Content-Type", "application/json");
        intercambio.sendResponseHeaders(estado, bytes.length);
        intercambio.getResponseBody().write(bytes);
        intercambio.close();
    }
}
