package com.alerta.sistema_alerta_inundaciones;

import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaService;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class EvaluacionRiesgoServiceTest {

    @Test
    void testEvaluacionRiesgoNiveles() throws Exception {
        AlertaService service = new AlertaService(null, null);
        long reachId = 710039498L;

        // 1. Simular 10 m3/s -> NORMAL
        AlertaRioDTO sim10 = service.simularInundacion(reachId, 10.0);
        assertEquals("NORMAL", sim10.getNivelAlerta());
        assertEquals(15.0, sim10.getUmbralVigilancia());
        assertEquals(30.0, sim10.getUmbralAlerta());
        assertEquals(60.0, sim10.getUmbralEmergencia());

        // 2. Simular 25 m3/s -> VIGILANCIA
        AlertaRioDTO sim25 = service.simularInundacion(reachId, 25.0);
        assertEquals("VIGILANCIA", sim25.getNivelAlerta());

        // 3. Simular 30 m3/s -> ALERTA (RIESGO DE INUNDACION)
        AlertaRioDTO sim30 = service.simularInundacion(reachId, 30.0);
        assertEquals("ALERTA", sim30.getNivelAlerta());

        // 4. Simular 50 m3/s -> ALERTA (RIESGO DE INUNDACION)
        AlertaRioDTO sim50 = service.simularInundacion(reachId, 50.0);
        assertEquals("ALERTA", sim50.getNivelAlerta());

        // 5. Simular 75 m3/s -> EMERGENCIA (PELIGRO DE INUNDACION)
        AlertaRioDTO sim75 = service.simularInundacion(reachId, 75.0);
        assertEquals("EMERGENCIA", sim75.getNivelAlerta());
    }

    @Test
    void testSeparacionEstadoRealYSimulado() throws Exception {
        AlertaService service = new AlertaService(null, null);
        long reachId = 710039498L;

        // Simulación extrema
        AlertaRioDTO sim = service.simularInundacion(reachId, 150.0);
        assertEquals("EMERGENCIA", sim.getNivelAlerta());
        assertEquals(150.0, sim.getCaudalMaximoPronosticado());

        // Simulación normal
        AlertaRioDTO simNorm = service.simularInundacion(reachId, 12.0);
        assertEquals("NORMAL", simNorm.getNivelAlerta());
        assertEquals(12.0, simNorm.getCaudalMaximoPronosticado());
    }
}
