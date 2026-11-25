package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.ClienteApiGeoglows;
import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.riesgo.DatosRiesgoTramo;
import com.alerta.sistema_alerta_inundaciones.riesgo.EstadoDatos;
import com.alerta.sistema_alerta_inundaciones.riesgo.EvaluacionRiesgo;
import com.alerta.sistema_alerta_inundaciones.riesgo.EvaluadorRiesgo;
import com.alerta.sistema_alerta_inundaciones.riesgo.ProveedorDatosRiesgo;
import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertaServiceTest {

    private static final long RIO_ID = 1L;
    private static final long REACH_ID = 710039498L;

    @Mock
    private SuscripcionAlertaRepository suscripcionRepository;
    @Mock
    private RioRepository rioRepository;
    @Mock
    private ProveedorDatosRiesgo proveedorDatos;
    @Mock
    private EvaluadorRiesgo evaluador;
    @Mock
    private ClienteApiGeoglows clienteGeoglows;
    @Mock
    private AlertaEventoRepository eventoRepository;
    @Mock
    private UsuarioRioRepository usuarioRioRepository;

    private AlertaService servicio;

    @BeforeEach
    void preparar() {
        servicio = new AlertaService(suscripcionRepository, rioRepository, proveedorDatos,
                evaluador, clienteGeoglows, eventoRepository, usuarioRioRepository);
    }

    @Test
    void marcaSiguiendoCuandoElUsuarioSigueElRio() throws Exception {
        prepararConsulta();
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);
        when(usuarioRioRepository.existsByUsuarioIdAndRioId(7L, RIO_ID)).thenReturn(true);

        AlertaRioDTO alerta = servicio.consultarAlertaPorRioId(RIO_ID, usuario);

        assertEquals(Boolean.TRUE, alerta.getSiguiendo());
        assertEquals(RIO_ID, alerta.getRioId());
    }

    @Test
    void marcaSiguiendoFalsoCuandoElUsuarioNoSigueElRio() throws Exception {
        prepararConsulta();
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(7L);
        when(usuarioRioRepository.existsByUsuarioIdAndRioId(7L, RIO_ID)).thenReturn(false);

        AlertaRioDTO alerta = servicio.consultarAlertaPorRioId(RIO_ID, usuario);

        assertEquals(Boolean.FALSE, alerta.getSiguiendo());
    }

    @Test
    void dejaSiguiendoEnNullSinSesion() throws Exception {
        prepararConsulta();

        AlertaRioDTO alerta = servicio.consultarAlertaPorRioId(RIO_ID);

        assertNull(alerta.getSiguiendo());
    }

    private void prepararConsulta() throws Exception {
        Rio rio = new Rio("Río Abajo", REACH_ID, 9.0, -79.48);
        when(rioRepository.findById(RIO_ID)).thenReturn(Optional.of(rio));

        Instant emision = Instant.parse("2026-10-23T00:00:00Z");
        DatosRiesgoTramo datos = new DatosRiesgoTramo(
                REACH_ID, "Río Abajo", 9.0, -79.48,
                10.0, 12.0, 15.0, 30.0, 60.0,
                emision, "GEOGloWS", "GEOGloWS - periodos de retorno",
                List.of(new DatosRiesgoTramo.PuntoCaudal(emision, 10.0)),
                null, List.of());
        when(proveedorDatos.obtener(REACH_ID, "Río Abajo", 9.0, -79.48)).thenReturn(datos);

        EvaluacionRiesgo evaluacion = new EvaluacionRiesgo(
                REACH_ID, "Río Abajo", NivelAlerta.NORMAL, NivelAlerta.NORMAL, NivelAlerta.NORMAL,
                true, EstadoDatos.PARCIALES, 10.0, 12.0, 15.0, 30.0, 60.0,
                null, null, Instant.parse("2026-10-23T00:05:00Z"),
                emision, null, "GEOGloWS", "No disponible", "GEOGloWS - periodos de retorno",
                List.of(), List.of(), "Sin riesgo.");
        when(evaluador.evaluar(datos)).thenReturn(evaluacion);
        when(eventoRepository.findTopByReachIdOrderByFechaCreacionDesc(REACH_ID))
                .thenReturn(Optional.empty());
    }
}
