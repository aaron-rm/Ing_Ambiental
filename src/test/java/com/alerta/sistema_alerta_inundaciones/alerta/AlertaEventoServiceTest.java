package com.alerta.sistema_alerta_inundaciones.alerta;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.config.PropiedadesMonitoreo;
import com.alerta.sistema_alerta_inundaciones.riesgo.DatosRiesgoTramo;
import com.alerta.sistema_alerta_inundaciones.riesgo.EstadoDatos;
import com.alerta.sistema_alerta_inundaciones.riesgo.EvaluacionRiesgo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlertaEventoServiceTest {

    private static final long REACH_ID = 710039498L;

    @Mock
    private EstadoRiesgoTramoRepository estadoRepository;

    @Mock
    private AlertaEventoRepository eventoRepository;

    private PropiedadesMonitoreo propiedades;
    private AlertaEventoService service;

    @BeforeEach
    void preparar() {
        propiedades = new PropiedadesMonitoreo();
        service = new AlertaEventoService(estadoRepository, eventoRepository, propiedades);
        when(estadoRepository.save(any(EstadoRiesgoTramo.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(eventoRepository.save(any(AlertaEvento.class)))
                .thenAnswer(invocacion -> {
                    AlertaEvento evento = invocacion.getArgument(0);
                    evento.setId(99L);
                    return evento;
                });
    }

    @Test
    void primeraEvaluacionDeAlertaCreaEventoPendienteYGuardaEstado() {
        when(estadoRepository.buscarParaActualizar(REACH_ID)).thenReturn(Optional.empty());
        when(eventoRepository.findByClaveIdempotencia(anyString())).thenReturn(Optional.empty());

        Optional<AlertaEvento> resultado = service.registrarEvaluacion(
                datos(), evaluacion(NivelAlerta.ALERTA, true, EstadoDatos.COMPLETOS), "PROGRAMADO");

        assertTrue(resultado.isPresent());
        AlertaEvento evento = resultado.get();
        assertEquals(NivelAlerta.ALERTA, evento.getNivel());
        assertEquals(NivelAlerta.NORMAL, evento.getNivelAnterior());
        assertEquals(EstadoEnvio.PENDIENTE, evento.getEstadoEnvio());
        assertNotNull(evento.getClaveIdempotencia());
        assertTrue(evento.getClaveIdempotencia().contains("NORMAL->ALERTA"));

        ArgumentCaptor<EstadoRiesgoTramo> captor = ArgumentCaptor.forClass(EstadoRiesgoTramo.class);
        verify(estadoRepository).save(captor.capture());
        assertEquals(NivelAlerta.ALERTA, captor.getValue().getNivelActual());
        assertTrue(captor.getValue().isEvaluacionConfiable());
        assertEquals(99L, captor.getValue().getUltimaAlertaId());
    }

    @Test
    void vigilanciaSeRegistraEnHistorialPeroNoSeNotifica() {
        when(estadoRepository.buscarParaActualizar(REACH_ID)).thenReturn(Optional.empty());
        when(eventoRepository.findByClaveIdempotencia(anyString())).thenReturn(Optional.empty());

        Optional<AlertaEvento> resultado = service.registrarEvaluacion(
                datos(), evaluacion(NivelAlerta.VIGILANCIA, true, EstadoDatos.COMPLETOS), "PROGRAMADO");

        assertTrue(resultado.isPresent());
        assertEquals(EstadoEnvio.OMITIDA, resultado.get().getEstadoEnvio());
    }

    @Test
    void mismoNivelSinCambioNoCreaEventoNuevo() {
        EstadoRiesgoTramo estado = estadoExistente(NivelAlerta.ALERTA);
        when(estadoRepository.buscarParaActualizar(REACH_ID)).thenReturn(Optional.of(estado));

        Optional<AlertaEvento> resultado = service.registrarEvaluacion(
                datos(), evaluacion(NivelAlerta.ALERTA, true, EstadoDatos.COMPLETOS), "PROGRAMADO");

        assertTrue(resultado.isEmpty());
        verify(eventoRepository, never()).save(any(AlertaEvento.class));
        verify(eventoRepository, never()).findByClaveIdempotencia(anyString());
    }

    @Test
    void recuperacionTrasAlertaCreaEventoNotificable() {
        EstadoRiesgoTramo estado = estadoExistente(NivelAlerta.ALERTA);
        when(estadoRepository.buscarParaActualizar(REACH_ID)).thenReturn(Optional.of(estado));
        when(eventoRepository.findByClaveIdempotencia(anyString())).thenReturn(Optional.empty());

        Optional<AlertaEvento> resultado = service.registrarEvaluacion(
                datos(), evaluacion(NivelAlerta.NORMAL, true, EstadoDatos.COMPLETOS), "PROGRAMADO");

        assertTrue(resultado.isPresent());
        assertEquals(EstadoEnvio.PENDIENTE, resultado.get().getEstadoEnvio());
        assertEquals(NivelAlerta.ALERTA, resultado.get().getNivelAnterior());
    }

    @Test
    void recuperacionPuedeConfigurarseParaNoNotificar() {
        propiedades.setNotificarRecuperacion(false);
        EstadoRiesgoTramo estado = estadoExistente(NivelAlerta.EMERGENCIA);
        when(estadoRepository.buscarParaActualizar(REACH_ID)).thenReturn(Optional.of(estado));
        when(eventoRepository.findByClaveIdempotencia(anyString())).thenReturn(Optional.empty());

        Optional<AlertaEvento> resultado = service.registrarEvaluacion(
                datos(), evaluacion(NivelAlerta.NORMAL, true, EstadoDatos.COMPLETOS), "PROGRAMADO");

        assertTrue(resultado.isPresent());
        assertEquals(EstadoEnvio.OMITIDA, resultado.get().getEstadoEnvio());
    }

    @Test
    void claveIdempotenteExistenteEvitaDuplicarElEvento() {
        EstadoRiesgoTramo estado = estadoExistente(NivelAlerta.ALERTA);
        AlertaEvento existente = new AlertaEvento();
        existente.setId(500L);
        when(estadoRepository.buscarParaActualizar(REACH_ID)).thenReturn(Optional.of(estado));
        when(eventoRepository.findByClaveIdempotencia(anyString())).thenReturn(Optional.of(existente));

        Optional<AlertaEvento> resultado = service.registrarEvaluacion(
                datos(), evaluacion(NivelAlerta.NORMAL, true, EstadoDatos.COMPLETOS), "PROGRAMADO");

        assertTrue(resultado.isPresent());
        assertEquals(500L, resultado.get().getId());
        verify(eventoRepository, never()).save(any(AlertaEvento.class));
    }

    @Test
    void evaluacionNoConfiableNoCreaEventoNiPierdeElNivelAnterior() {
        EstadoRiesgoTramo estado = estadoExistente(NivelAlerta.ALERTA);
        when(estadoRepository.buscarParaActualizar(REACH_ID)).thenReturn(Optional.of(estado));

        Optional<AlertaEvento> resultado = service.registrarEvaluacion(
                datos(), evaluacion(NivelAlerta.EMERGENCIA, false, EstadoDatos.DESACTUALIZADOS), "PROGRAMADO");

        assertTrue(resultado.isEmpty());
        verify(eventoRepository, never()).save(any(AlertaEvento.class));
        assertEquals(NivelAlerta.ALERTA, estado.getNivelActual());
        assertFalse(estado.isEvaluacionConfiable());
    }

    @Test
    void reglasDeNotificacionDependenDelNivelYDeLaRecuperacion() {
        assertFalse(service.debeNotificar(NivelAlerta.VIGILANCIA, NivelAlerta.NORMAL));
        assertTrue(service.debeNotificar(NivelAlerta.ALERTA, NivelAlerta.NORMAL));
        assertTrue(service.debeNotificar(NivelAlerta.EMERGENCIA, NivelAlerta.ALERTA));
        assertTrue(service.debeNotificar(NivelAlerta.NORMAL, NivelAlerta.ALERTA));
        assertFalse(service.debeNotificar(NivelAlerta.NORMAL, NivelAlerta.VIGILANCIA));
        assertFalse(service.debeNotificar(NivelAlerta.NORMAL, NivelAlerta.NORMAL));

        propiedades.setNotificarRecuperacion(false);
        assertFalse(service.debeNotificar(NivelAlerta.NORMAL, NivelAlerta.EMERGENCIA));
    }

    private EstadoRiesgoTramo estadoExistente(NivelAlerta nivel) {
        EstadoRiesgoTramo estado = new EstadoRiesgoTramo();
        estado.setId(10L);
        estado.setReachId(REACH_ID);
        estado.setNivelActual(nivel);
        estado.setEvaluacionConfiable(true);
        return estado;
    }

    private DatosRiesgoTramo datos() {
        Instant fecha = Instant.now().minusSeconds(60);
        return new DatosRiesgoTramo(
                REACH_ID, "Río de prueba", 9.08, -79.28,
                5.0, 75.0, 15.0, 30.0, 60.0,
                fecha, "GEOGloWS forecaststats", "GEOGloWS - periodos de retorno",
                List.of(new DatosRiesgoTramo.PuntoCaudal(fecha, 75.0)),
                null, List.of());
    }

    private EvaluacionRiesgo evaluacion(NivelAlerta nivel, boolean confiable, EstadoDatos estadoDatos) {
        Instant ahora = Instant.now();
        return new EvaluacionRiesgo(
                REACH_ID, "Río de prueba", nivel, nivel, NivelAlerta.NORMAL,
                confiable, estadoDatos, 5.0, 75.0, 15.0, 30.0, 60.0,
                10.0, 20.0, ahora, ahora.minusSeconds(60), ahora,
                "GEOGloWS forecaststats", "Open-Meteo", "GEOGloWS - periodos de retorno",
                List.of("Caudal máximo previsto"), List.of(), "Mensaje de prueba");
    }
}
