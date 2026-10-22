package com.alerta.sistema_alerta_inundaciones;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.ClienteApiGeoglows;
import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaService;
import com.alerta.sistema_alerta_inundaciones.config.PropiedadesRiesgo;
import com.alerta.sistema_alerta_inundaciones.riesgo.EvaluadorRiesgo;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pruebas de la simulación sin llamadas de red. La simulación es la única
 * parte de AlertaService que no toca repositorios ni persistencia.
 */
class EvaluacionRiesgoServiceTest {

    private static final long REACH_ID = 710039498L;

    @Test
    void simulacionUsaUmbralesDeReferenciaSiFallaGeoglows() throws Exception {
        ClienteApiGeoglows cliente = mock(ClienteApiGeoglows.class);
        when(cliente.obtenerUmbrales(anyLong())).thenThrow(new IOException("GEOGloWS no disponible"));

        AlertaService service = new AlertaService(
                null, null, null,
                new EvaluadorRiesgo(new PropiedadesRiesgo()),
                cliente, null);

        AlertaRioDTO normal = service.simularInundacion(REACH_ID, 10.0);
        assertEquals("NORMAL", normal.getNivelAlerta());
        assertEquals(15.0, normal.getUmbralVigilancia());
        assertEquals(30.0, normal.getUmbralAlerta());
        assertEquals(60.0, normal.getUmbralEmergencia());
        assertTrue(normal.isSimulacion());
        assertTrue(normal.getFuenteUmbrales().toLowerCase().contains("referencia"));

        assertEquals("VIGILANCIA", service.simularInundacion(REACH_ID, 25.0).getNivelAlerta());
        assertEquals("ALERTA", service.simularInundacion(REACH_ID, 30.0).getNivelAlerta());
        assertEquals("ALERTA", service.simularInundacion(REACH_ID, 50.0).getNivelAlerta());
        assertEquals("EMERGENCIA", service.simularInundacion(REACH_ID, 75.0).getNivelAlerta());
    }

    @Test
    void simulacionUsaUmbralesDeGeoglowsCuandoEstanDisponibles() throws Exception {
        ClienteApiGeoglows cliente = mock(ClienteApiGeoglows.class);
        LectorDatosIngresados.UmbralAlerta umbrales = new LectorDatosIngresados.UmbralAlerta();
        umbrales.reachId = REACH_ID;
        umbrales.caudalVigilancia = 100.0;
        umbrales.caudalAlerta = 200.0;
        umbrales.caudalEmergencia = 300.0;
        umbrales.fuenteCalibracion = "GEOGloWS - periodos de retorno";
        when(cliente.obtenerUmbrales(REACH_ID)).thenReturn(umbrales);

        AlertaService service = new AlertaService(
                null, null, null,
                new EvaluadorRiesgo(new PropiedadesRiesgo()),
                cliente, null);

        AlertaRioDTO alerta = service.simularInundacion(REACH_ID, 250.0);
        assertEquals("ALERTA", alerta.getNivelAlerta());
        assertEquals(100.0, alerta.getUmbralVigilancia());
        assertEquals(200.0, alerta.getUmbralAlerta());
        assertEquals(300.0, alerta.getUmbralEmergencia());
        assertEquals("GEOGloWS - periodos de retorno", alerta.getFuenteUmbrales());

        assertEquals("EMERGENCIA", service.simularInundacion(REACH_ID, 350.0).getNivelAlerta());
    }

    @Test
    void simulacionNoSePresentaComoDatoReal() throws Exception {
        ClienteApiGeoglows cliente = mock(ClienteApiGeoglows.class);
        when(cliente.obtenerUmbrales(anyLong())).thenThrow(new IOException("sin red"));

        AlertaService service = new AlertaService(
                null, null, null,
                new EvaluadorRiesgo(new PropiedadesRiesgo()),
                cliente, null);

        AlertaRioDTO sim = service.simularInundacion(REACH_ID, 150.0);
        assertTrue(sim.isSimulacion());
        assertEquals("SIMULACION", sim.getEstadoDatos());
        assertTrue(sim.getMensaje().startsWith("SIMULACION:"));
        assertTrue(sim.getFuenteCaudal().toLowerCase().contains("simulado"));
    }
}
