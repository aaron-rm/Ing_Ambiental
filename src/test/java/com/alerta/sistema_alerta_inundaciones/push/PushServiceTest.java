package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEvento;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEventoRepository;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;
import com.alerta.sistema_alerta_inundaciones.alerta.EstadoEnvio;
import com.alerta.sistema_alerta_inundaciones.config.PropiedadesFirebase;
import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushServiceTest {

    private static final long REACH_ID = 710039498L;
    private static final long RIO_ID = 4L;

    @Mock
    private DispositivoPushRepository dispositivoRepository;

    @Mock
    private RioRepository rioRepository;

    @Mock
    private UsuarioRioRepository seleccionRepository;

    @Mock
    private AlertaEventoRepository eventoRepository;

    @Mock
    private PushSender pushSender;

    private PushService service;

    @BeforeEach
    void preparar() {
        service = new PushService(
                dispositivoRepository, rioRepository, seleccionRepository,
                eventoRepository, pushSender, new PropiedadesFirebase());
        when(eventoRepository.save(any(AlertaEvento.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(dispositivoRepository.save(any(DispositivoPush.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void sinRioConfiguradoNoSeEnviaYNadieEsObjetivo() {
        when(rioRepository.findFirstByGeoglowsId(REACH_ID)).thenReturn(Optional.empty());

        AlertaEvento evento = evento();
        service.enviarParaEvento(evento);

        assertEquals(EstadoEnvio.SIN_DISPOSITIVOS, evento.getEstadoEnvio());
        assertEquals(0, evento.getDispositivosObjetivo());
        verify(pushSender, never()).enviar(any(), anyString());
    }

    @Test
    void sinSeguidoresDelRioNoSeEnvia() {
        when(rioRepository.findFirstByGeoglowsId(REACH_ID))
                .thenReturn(Optional.of(new Rio("Río de prueba", REACH_ID, 9.08, -79.28)));
        when(seleccionRepository.findUsuarioIdsByRioId(RIO_ID)).thenReturn(List.of());

        AlertaEvento evento = evento();
        service.enviarParaEvento(evento);

        assertEquals(EstadoEnvio.SIN_DISPOSITIVOS, evento.getEstadoEnvio());
        verify(dispositivoRepository, never()).findByUsuarioIdInAndActivoTrue(any());
    }

    @Test
    void enviaSoloALosDispositivosDeUsuariosQueSiguenElRio() {
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true, usuario(1L));
        when(rioRepository.findFirstByGeoglowsId(REACH_ID))
                .thenReturn(Optional.of(new Rio("Río de prueba", REACH_ID, 9.08, -79.28)));
        when(seleccionRepository.findUsuarioIdsByRioId(any())).thenReturn(List.of(1L, 1L, 2L));
        when(dispositivoRepository.findByUsuarioIdInAndActivoTrue(List.of(1L, 2L)))
                .thenReturn(List.of(dispositivo));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.ok());

        AlertaEvento evento = evento();
        service.enviarParaEvento(evento);

        assertEquals(EstadoEnvio.ENVIADA, evento.getEstadoEnvio());
        assertEquals(1, evento.getDispositivosEnviados());
    }

    @Test
    void fcmDeshabilitadoSeRegistraEnElEventoSinPropagarLaExcepcion() {
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true, usuario(1L));
        when(rioRepository.findFirstByGeoglowsId(REACH_ID))
                .thenReturn(Optional.of(new Rio("Río de prueba", REACH_ID, 9.08, -79.28)));
        when(seleccionRepository.findUsuarioIdsByRioId(any())).thenReturn(List.of(1L));
        when(dispositivoRepository.findByUsuarioIdInAndActivoTrue(List.of(1L)))
                .thenReturn(List.of(dispositivo));
        when(pushSender.estaHabilitado()).thenReturn(false);
        when(pushSender.motivoDeshabilitado()).thenReturn("Faltan credenciales de Firebase.");

        AlertaEvento evento = evento();
        service.enviarParaEvento(evento);

        assertEquals(EstadoEnvio.DESHABILITADO, evento.getEstadoEnvio());
        assertEquals(1, evento.getDispositivosObjetivo());
        assertTrue(evento.getErrorEnvio().contains("credenciales"));
        verify(pushSender, never()).enviar(any(), anyString());
    }

    @Test
    void tokenInvalidoSeDesactivaYElEnvioQuedaParcial() {
        DispositivoPush valido = dispositivo("token-1", "device-1", true, usuario(1L));
        DispositivoPush invalido = dispositivo("token-2", "device-2", true, usuario(2L));
        when(rioRepository.findFirstByGeoglowsId(REACH_ID))
                .thenReturn(Optional.of(new Rio("Río de prueba", REACH_ID, 9.08, -79.28)));
        when(seleccionRepository.findUsuarioIdsByRioId(any())).thenReturn(List.of(1L, 2L));
        when(dispositivoRepository.findByUsuarioIdInAndActivoTrue(List.of(1L, 2L)))
                .thenReturn(List.of(valido, invalido));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.ok());
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-2")))
                .thenReturn(ResultadoEnvioPush.tokenInvalido("Token no registrado."));

        AlertaEvento evento = evento();
        service.enviarParaEvento(evento);

        assertEquals(EstadoEnvio.PARCIAL, evento.getEstadoEnvio());
        assertFalse(invalido.isActivo());
        verify(dispositivoRepository).save(invalido);
    }

    @Test
    void registrarUnTokenExistenteDelMismoDispositivoLoReasignaAlUsuario() {
        Usuario usuario = usuario(1L);
        DispositivoPush existente = dispositivo("token-1", "device-1", true, usuario(2L));
        when(dispositivoRepository.findByToken("token-1")).thenReturn(Optional.of(existente));
        when(dispositivoRepository.save(existente)).thenReturn(existente);

        RegistroPushResponse respuesta = service.registrar(usuario,
                solicitud("token-1", "device-1"));

        assertTrue(respuesta.activo());
        assertEquals(usuario, existente.getUsuario());
        assertEquals(0L, existente.getReachId());
        verify(dispositivoRepository).save(existente);
    }

    @Test
    void registrarRechazaUnTokenYaAsociadoAOtroDispositivo() {
        DispositivoPush existente = dispositivo("token-1", "otro-device", true, usuario(2L));
        when(dispositivoRepository.findByToken("token-1")).thenReturn(Optional.of(existente));

        assertThrows(IllegalStateException.class,
                () -> service.registrar(usuario(1L), solicitud("token-1", "device-1")));
    }

    @Test
    void registrarUnTokenNuevoDesactivaLosTokensAnterioresDelMismoDispositivo() {
        when(dispositivoRepository.findByToken("token-nuevo")).thenReturn(Optional.empty());
        DispositivoPush anterior = dispositivo("token-viejo", "device-1", true, usuario(1L));
        when(dispositivoRepository.findByDeviceIdAndActivoTrue("device-1"))
                .thenReturn(List.of(anterior));

        RegistroPushResponse respuesta = service.registrar(usuario(1L),
                solicitud("token-nuevo", "device-1"));

        assertTrue(respuesta.activo());
        assertFalse(anterior.isActivo());
        verify(dispositivoRepository).save(anterior);
    }

    @Test
    void desactivarRechazaUnTokenDeOtroUsuario() {
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true, usuario(2L));
        when(dispositivoRepository.findByToken("token-1")).thenReturn(Optional.of(dispositivo));

        assertThrows(IllegalStateException.class,
                () -> service.desactivar(usuario(1L), "token-1", "device-1"));
    }

    @Test
    void pruebaTieneLimiteDeSesentaSegundos() {
        Usuario usuario = usuario(1L);
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true, usuario);
        when(dispositivoRepository.findByToken("token-1")).thenReturn(Optional.of(dispositivo));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.ok());

        PruebaPushRequest solicitud = new PruebaPushRequest();
        solicitud.setToken("token-1");
        solicitud.setDeviceId("device-1");

        assertTrue(service.enviarPrueba(usuario, solicitud).exito());
        assertThrows(LimitePruebaExcedidoException.class,
                () -> service.enviarPrueba(usuario, solicitud));
    }

    @Test
    void pruebaConTokenInvalidoDesactivaElDispositivo() {
        Usuario usuario = usuario(1L);
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true, usuario);
        when(dispositivoRepository.findByToken("token-1")).thenReturn(Optional.of(dispositivo));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.tokenInvalido("Token inválido."));

        PruebaPushRequest solicitud = new PruebaPushRequest();
        solicitud.setToken("token-1");
        solicitud.setDeviceId("device-1");

        ResultadoEnvioPush resultado = service.enviarPrueba(usuario, solicitud);

        assertTrue(resultado.tokenInvalido());
        assertFalse(dispositivo.isActivo());
        verify(dispositivoRepository).save(dispositivo);
    }

    @Test
    void demoEnviaATodosLosDispositivosActivos() {
        DispositivoPush primero = dispositivo("token-1", "device-1", true, usuario(1L));
        DispositivoPush segundo = dispositivo("token-2", "device-2", true, usuario(2L));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(dispositivoRepository.findByActivoTrue()).thenReturn(List.of(primero, segundo));
        when(pushSender.enviar(any(NotificacionPush.class), anyString()))
                .thenReturn(ResultadoEnvioPush.ok());

        ResultadoDemoPush resultado = service.enviarDemoATodos();

        assertEquals(2, resultado.dispositivos());
        assertEquals(2, resultado.enviados());
        assertEquals(0, resultado.fallidos());
    }

    @Test
    void demoConFcmDeshabilitadoLanzaErrorClaro() {
        when(pushSender.estaHabilitado()).thenReturn(false);
        when(pushSender.motivoDeshabilitado()).thenReturn("Faltan credenciales de Firebase.");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.enviarDemoATodos());
        assertTrue(error.getMessage().contains("credenciales"));
    }

    @Test
    void simulacionEnviaSoloALosSeguidoresDelRioSimulado() {
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true, usuario(1L));
        when(rioRepository.findFirstByGeoglowsId(REACH_ID))
                .thenReturn(Optional.of(new Rio("Río de prueba", REACH_ID, 9.08, -79.28)));
        when(seleccionRepository.findUsuarioIdsByRioId(any())).thenReturn(List.of(1L));
        when(dispositivoRepository.findByUsuarioIdInAndActivoTrue(List.of(1L)))
                .thenReturn(List.of(dispositivo));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.ok());

        ResultadoDemoPush resultado = service.enviarSimulacion(simulacion());

        assertEquals(1, resultado.dispositivos());
        assertEquals(1, resultado.enviados());
        assertEquals(0, resultado.fallidos());
        verify(pushSender).enviar(
                argThat(notificacion -> notificacion.titulo().contains("SIMULACRO")
                        && notificacion.reachId() == REACH_ID
                        && notificacion.eventoId() == 0L),
                eq("token-1"));
    }

    @Test
    void simulacionSinSeguidoresNoEnviaNada() {
        when(rioRepository.findFirstByGeoglowsId(REACH_ID))
                .thenReturn(Optional.of(new Rio("Río de prueba", REACH_ID, 9.08, -79.28)));
        when(seleccionRepository.findUsuarioIdsByRioId(RIO_ID)).thenReturn(List.of());
        when(pushSender.estaHabilitado()).thenReturn(true);

        ResultadoDemoPush resultado = service.enviarSimulacion(simulacion());

        assertEquals(0, resultado.dispositivos());
        assertEquals(0, resultado.enviados());
        verify(pushSender, never()).enviar(any(), anyString());
    }

    @Test
    void simulacionConFcmDeshabilitadoLanzaErrorClaro() {
        when(pushSender.estaHabilitado()).thenReturn(false);
        when(pushSender.motivoDeshabilitado()).thenReturn("Faltan credenciales de Firebase.");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.enviarSimulacion(simulacion()));
        assertTrue(error.getMessage().contains("credenciales"));
    }

    private AlertaRioDTO simulacion() {
        AlertaRioDTO alerta = new AlertaRioDTO();
        alerta.setReachId(REACH_ID);
        alerta.setRioId(RIO_ID);
        alerta.setNombreRio("Río de prueba");
        alerta.setNivelAlerta("EMERGENCIA");
        alerta.setSimulacion(true);
        return alerta;
    }

    private RegistroPushRequest solicitud(String token, String deviceId) {
        RegistroPushRequest solicitud = new RegistroPushRequest();
        solicitud.setToken(token);
        solicitud.setDeviceId(deviceId);
        solicitud.setPlataforma("web");
        return solicitud;
    }

    private DispositivoPush dispositivo(String token, String deviceId, boolean activo, Usuario usuario) {
        DispositivoPush dispositivo = new DispositivoPush();
        dispositivo.setId(1L);
        dispositivo.setToken(token);
        dispositivo.setDeviceId(deviceId);
        dispositivo.setUsuario(usuario);
        dispositivo.setReachId(REACH_ID);
        dispositivo.setActivo(activo);
        dispositivo.setPlataforma("web");
        dispositivo.setFechaRegistro(Instant.now());
        dispositivo.setFechaActualizacion(Instant.now());
        return dispositivo;
    }

    private Usuario usuario(Long id) {
        Usuario usuario = mock(Usuario.class);
        when(usuario.getId()).thenReturn(id);
        when(usuario.getIdentificador()).thenReturn("usuario" + id);
        return usuario;
    }

    private AlertaEvento evento() {
        AlertaEvento evento = new AlertaEvento();
        evento.setId(5L);
        evento.setReachId(REACH_ID);
        evento.setNombreRio("Río de prueba");
        evento.setNivel(NivelAlerta.ALERTA);
        evento.setNivelAnterior(NivelAlerta.VIGILANCIA);
        evento.setCaudalMaximo(75.0);
        evento.setFechaCreacion(Instant.now());
        evento.setEstadoEnvio(EstadoEnvio.PENDIENTE);
        return evento;
    }
}
