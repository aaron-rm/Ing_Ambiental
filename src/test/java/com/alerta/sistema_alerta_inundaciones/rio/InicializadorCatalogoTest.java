package com.alerta.sistema_alerta_inundaciones.rio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InicializadorCatalogoTest {

    @Test
    void cargaLosCuatroRiosDelCatalogoConGeometriaReal() throws Exception {
        RioRepository repositorio = mock(RioRepository.class);
        when(repositorio.findFirstByGeoglowsId(anyLong())).thenReturn(Optional.empty());
        when(repositorio.save(any(Rio.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(repositorio.count()).thenReturn(4L);

        new InicializadorCatalogo(repositorio, true).run();

        ArgumentCaptor<Rio> captor = ArgumentCaptor.forClass(Rio.class);
        verify(repositorio, org.mockito.Mockito.times(4)).save(captor.capture());
        List<Rio> rios = captor.getAllValues();

        assertEquals(List.of("Río Abajo", "Río Curundú", "Río Juan Díaz", "Río Tocumen"),
                rios.stream().map(Rio::getNombre).toList());
        assertEquals(List.of(710039498L, 710037516L, 710051399L, 670053261L),
                rios.stream().map(Rio::getGeoglowsId).toList());

        ObjectMapper mapper = new ObjectMapper();
        for (Rio rio : rios) {
            JsonNode geometria = mapper.readTree(rio.getGeometria());
            assertTrue(List.of("LineString", "MultiLineString").contains(geometria.get("type").asText()),
                    "Geometría inesperada para " + rio.getNombre());
            assertTrue(rio.getLatitud() >= 8.9 && rio.getLatitud() <= 9.2,
                    "Latitud fuera de Panamá para " + rio.getNombre());
            assertTrue(rio.getLongitud() >= -79.7 && rio.getLongitud() <= -79.2,
                    "Longitud fuera de Panamá para " + rio.getNombre());
        }
    }

    @Test
    void actualizaElNombreDelRioHeredadoSinDuplicarlo() {
        RioRepository repositorio = mock(RioRepository.class);
        Rio existente = new Rio("Rio Pacora (Panama)", 710039498L, 9.08, -79.28);
        when(repositorio.findFirstByGeoglowsId(anyLong())).thenReturn(Optional.empty());
        when(repositorio.findFirstByGeoglowsId(710039498L)).thenReturn(Optional.of(existente));
        when(repositorio.save(any(Rio.class))).thenAnswer(invocacion -> invocacion.getArgument(0));
        when(repositorio.count()).thenReturn(4L);

        new InicializadorCatalogo(repositorio, true).run();

        assertEquals("Río Abajo", existente.getNombre());
        assertTrue(existente.getGeometria().contains("coordinates"));
    }

    @Test
    void sePuedeDesactivarLaCargaDelCatalogo() {
        RioRepository repositorio = mock(RioRepository.class);

        new InicializadorCatalogo(repositorio, false).run();

        verify(repositorio, never()).save(any(Rio.class));
    }
}
