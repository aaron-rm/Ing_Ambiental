package com.alerta.sistema_alerta_inundaciones.rio;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.ClienteApiGeoglows;
import com.alerta.sistema_alerta_inundaciones.usuario.Rol;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RioServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void obtenerMapaGeoJsonIncluyeRioIdNombreYGeometria() throws Exception {
        RioRepository repositorio = mock(RioRepository.class);
        Rio rio = new Rio("Río Abajo", 710039498L, 9.0, -79.48);
        rio.setGeometria("{\"type\":\"LineString\",\"coordinates\":[[-79.48,9.0],[-79.46,9.01]]}");
        when(repositorio.findAll()).thenReturn(List.of(rio));

        String geojson = new RioService(repositorio).obtenerMapaGeoJson();

        JsonNode coleccion = mapper.readTree(geojson);
        assertEquals("FeatureCollection", coleccion.get("type").asText());
        JsonNode feature = coleccion.get("features").get(0);
        assertEquals(710039498L, feature.get("properties").get("reachId").asLong());
        assertEquals("Río Abajo", feature.get("properties").get("nombre").asText());
        assertEquals("LineString", feature.get("geometry").get("type").asText());
    }

    @Test
    void obtenerMapaOmiteLosRiosSinGeometria() throws Exception {
        RioRepository repositorio = mock(RioRepository.class);
        Rio conGeometria = new Rio("Río Tocumen", 670053261L, 9.03, -79.4);
        conGeometria.setGeometria("{\"type\":\"LineString\",\"coordinates\":[[-79.4,9.03]]}");
        Rio sinGeometria = new Rio("Río sin geometría", 1L, 9.0, -79.0);
        when(repositorio.findAll()).thenReturn(List.of(conGeometria, sinGeometria));

        JsonNode coleccion = mapper.readTree(new RioService(repositorio).obtenerMapaGeoJson());

        assertThat(coleccion.get("features")).hasSize(1);
        assertEquals("Río Tocumen", coleccion.get("features").get(0).get("properties").get("nombre").asText());
    }

    @Test
    void obtenerPorIdFallaConMensajeClaro() {
        RioRepository repositorio = mock(RioRepository.class);
        when(repositorio.findById(7L)).thenReturn(Optional.empty());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new RioService(repositorio).obtenerPorId(7L));

        assertThat(error.getMessage()).contains("7");
    }

    @Test
    void crearRioSinTramoCercanoDevuelve422() throws Exception {
        RioRepository repositorio = mock(RioRepository.class);
        ClienteApiGeoglows cliente = mock(ClienteApiGeoglows.class);
        when(cliente.buscarReachIdCercano(9.0, -79.5)).thenReturn(null);

        GeometriaTramoClient geometriaClient = mock(GeometriaTramoClient.class);
        RioService service = new RioService(repositorio, cliente, geometriaClient);
        Usuario usuario = new Usuario("ana", "hash", Rol.USER);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.crearRio(new CrearRioRequest("Río Nuevo", 9.0, -79.5), usuario));

        assertEquals(422, error.getStatusCode().value());
        verify(repositorio, never()).save(any());
    }

    @Test
    void crearRioDuplicadoDevuelve409() throws Exception {
        RioRepository repositorio = mock(RioRepository.class);
        ClienteApiGeoglows cliente = mock(ClienteApiGeoglows.class);
        GeometriaTramoClient geometriaClient = mock(GeometriaTramoClient.class);
        when(cliente.buscarReachIdCercano(9.0, -79.48)).thenReturn(710039498L);
        when(repositorio.findFirstByGeoglowsId(710039498L))
                .thenReturn(Optional.of(new Rio("Río Abajo", 710039498L, 9.0, -79.48)));

        RioService service = new RioService(repositorio, cliente, geometriaClient);
        Usuario usuario = new Usuario("ana", "hash", Rol.USER);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.crearRio(new CrearRioRequest("Otro nombre", 9.0, -79.48), usuario));

        assertEquals(409, error.getStatusCode().value());
        assertThat(error.getReason()).contains("Río Abajo");
        verify(repositorio, never()).save(any());
    }

    @Test
    void crearRioGuardaConGeometriaNullYCreadoPor() throws Exception {
        RioRepository repositorio = mock(RioRepository.class);
        ClienteApiGeoglows cliente = mock(ClienteApiGeoglows.class);
        GeometriaTramoClient geometriaClient = mock(GeometriaTramoClient.class);
        when(cliente.buscarReachIdCercano(8.95, -79.55)).thenReturn(990011223L);
        when(repositorio.findFirstByGeoglowsId(990011223L)).thenReturn(Optional.empty());
        when(geometriaClient.buscar(990011223L)).thenReturn(Optional.empty());
        when(repositorio.save(any(Rio.class))).thenAnswer(inv -> inv.getArgument(0));

        RioService service = new RioService(repositorio, cliente, geometriaClient);
        Usuario usuario = new Usuario("Investigador1", "hash", Rol.USER);

        RioResumenDTO dto = service.crearRio(
                new CrearRioRequest("  Río Chilibre  ", 8.95, -79.55), usuario);

        ArgumentCaptor<Rio> captor = ArgumentCaptor.forClass(Rio.class);
        verify(repositorio).save(captor.capture());
        Rio guardado = captor.getValue();

        assertEquals("Río Chilibre", guardado.getNombre());
        assertEquals(990011223L, guardado.getGeoglowsId());
        assertNull(guardado.getGeometria());
        assertEquals("investigador1", guardado.getCreadoPor());
        assertEquals("Río Chilibre", dto.nombre());
        assertEquals(990011223L, dto.reachId());
        assertFalse(dto.tieneGeometria());
    }

    @Test
    void crearRioGuardaGeometriaCuandoElClienteLaDevuelve() throws Exception {
        RioRepository repositorio = mock(RioRepository.class);
        ClienteApiGeoglows cliente = mock(ClienteApiGeoglows.class);
        GeometriaTramoClient geometriaClient = mock(GeometriaTramoClient.class);
        String geometria = "{\"type\":\"LineString\",\"coordinates\":[[-79.55,8.95],[-79.54,8.96]]}";
        when(cliente.buscarReachIdCercano(8.95, -79.55)).thenReturn(990011223L);
        when(repositorio.findFirstByGeoglowsId(990011223L)).thenReturn(Optional.empty());
        when(geometriaClient.buscar(990011223L)).thenReturn(Optional.of(geometria));
        when(repositorio.save(any(Rio.class))).thenAnswer(inv -> inv.getArgument(0));

        RioService service = new RioService(repositorio, cliente, geometriaClient);
        Usuario usuario = new Usuario("ana", "hash", Rol.USER);

        RioResumenDTO dto = service.crearRio(
                new CrearRioRequest("Río Chilibre", 8.95, -79.55), usuario);

        ArgumentCaptor<Rio> captor = ArgumentCaptor.forClass(Rio.class);
        verify(repositorio).save(captor.capture());
        assertEquals(geometria, captor.getValue().getGeometria());
        assertTrue(dto.tieneGeometria());
    }

    @Test
    void renombrarRioRecortaElNombreYLoMarcaComoEditado() {
        RioRepository repositorio = mock(RioRepository.class);
        Rio rio = new Rio("Río Abajo", 710039498L, 9.0, -79.48);
        when(repositorio.findById(7L)).thenReturn(Optional.of(rio));
        when(repositorio.save(any(Rio.class))).thenAnswer(invocacion -> invocacion.getArgument(0));

        RioResumenDTO dto = new RioService(repositorio).renombrarRio(7L, new RenombrarRioRequest("  Río Abajo (Panamá)  "));

        assertEquals("Río Abajo (Panamá)", dto.nombre());
        assertEquals("Río Abajo (Panamá)", rio.getNombre());
        assertTrue(rio.isNombreEditado());
    }

    @Test
    void renombrarRioRechazaNombreVacioOLargo() {
        RioRepository repositorio = mock(RioRepository.class);
        RioService servicio = new RioService(repositorio);

        ResponseStatusException vacio = assertThrows(ResponseStatusException.class,
                () -> servicio.renombrarRio(1L, new RenombrarRioRequest("   ")));
        assertEquals(400, vacio.getStatusCode().value());
        ResponseStatusException largo = assertThrows(ResponseStatusException.class,
                () -> servicio.renombrarRio(1L, new RenombrarRioRequest("x".repeat(101))));
        assertEquals(400, largo.getStatusCode().value());
        assertThrows(ResponseStatusException.class, () -> servicio.renombrarRio(1L, null));
        verify(repositorio, never()).save(any(Rio.class));
    }

    @Test
    void renombrarRioInexistenteDevuelve404() {
        RioRepository repositorio = mock(RioRepository.class);
        when(repositorio.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> new RioService(repositorio).renombrarRio(99L, new RenombrarRioRequest("Nuevo")));

        assertEquals(404, e.getStatusCode().value());
    }
}
