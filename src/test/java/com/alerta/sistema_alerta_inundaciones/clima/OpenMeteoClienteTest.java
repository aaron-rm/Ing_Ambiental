package com.alerta.sistema_alerta_inundaciones.clima;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OpenMeteoClienteTest {

    @Test
    void interpretaSeriesHorariasYCalculaAcumulados() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://open-meteo.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(containsString("latitude=9.08")))
                .andRespond(withSuccess(respuestaHoraria(2.0), MediaType.APPLICATION_JSON));

        OpenMeteoCliente cliente = new OpenMeteoCliente(
                builder.build(), Duration.ofMinutes(15));
        DatosPrecipitacion datos = cliente.obtener(9.08, -79.28);

        assertTrue(datos.disponible());
        assertTrue(datos.lluvia24hMm() > 0);
        assertTrue(datos.lluvia72hMm() > datos.lluvia24hMm());
        assertEquals(2.0, datos.maxLluviaHorariaMm());
        assertEquals(72, datos.serie().size());
        servidor.verify();
    }

    @Test
    void reutilizaLaCacheDentroDelTtl() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://open-meteo.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(containsString("precipitation")))
                .andRespond(withSuccess(respuestaHoraria(1.0), MediaType.APPLICATION_JSON));

        OpenMeteoCliente cliente = new OpenMeteoCliente(
                builder.build(), Duration.ofMinutes(15));
        DatosPrecipitacion primera = cliente.obtener(9.08, -79.28);
        DatosPrecipitacion segunda = cliente.obtener(9.08, -79.28);

        assertTrue(primera.disponible());
        assertEquals(primera.lluvia24hMm(), segunda.lluvia24hMm());
        // Solo se consumió la primera expectativa: la segunda llamada salió de caché.
        servidor.verify();
    }

    @Test
    void respuestaConSeriesIncompletasNoSeConsideraDisponible() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://open-meteo.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(containsString("precipitation")))
                .andRespond(withSuccess(
                        "{\"hourly\":{\"time\":[],\"precipitation\":[]}}",
                        MediaType.APPLICATION_JSON));

        OpenMeteoCliente cliente = new OpenMeteoCliente(
                builder.build(), Duration.ofMinutes(15));
        DatosPrecipitacion datos = cliente.obtener(9.08, -79.28);

        assertFalse(datos.disponible());
        assertTrue(datos.advertencia().contains("series incompletas"));
    }

    @Test
    void unErrorHttpDegradaSinLanzarExcepcion() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://open-meteo.test");
        MockRestServiceServer servidor = MockRestServiceServer.bindTo(builder).build();
        servidor.expect(requestTo(containsString("precipitation")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        OpenMeteoCliente cliente = new OpenMeteoCliente(
                builder.build(), Duration.ofMinutes(15));
        DatosPrecipitacion datos = cliente.obtener(9.08, -79.28);

        assertFalse(datos.disponible());
        assertTrue(datos.advertencia().contains("No se pudo consultar Open-Meteo"));
    }

    private String respuestaHoraria(double mm) {
        LocalDateTime inicio = LocalDateTime.now(ZoneOffset.UTC)
                .withMinute(0)
                .withSecond(0)
                .withNano(0);
        StringBuilder tiempos = new StringBuilder();
        StringBuilder valores = new StringBuilder();
        for (int i = 0; i < 72; i++) {
            if (i > 0) {
                tiempos.append(',');
                valores.append(',');
            }
            tiempos.append('"').append(inicio.plusHours(i)).append('"');
            valores.append(mm);
        }
        return "{\"hourly\":{\"time\":[" + tiempos + "],\"precipitation\":[" + valores + "]}}";
    }
}
