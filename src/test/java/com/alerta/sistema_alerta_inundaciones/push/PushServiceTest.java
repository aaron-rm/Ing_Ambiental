package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEvento;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEventoRepository;
import com.alerta.sistema_alerta_inundaciones.alerta.EstadoEnvio;
import com.alerta.sistema_alerta_inundaciones.config.PropiedadesFirebase;
import com.alerta.sistema_alerta_inundaciones.rio.Rio;
import com.alerta.sistema_alerta_inundaciones.rio.RioRepository;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PushServiceTest {

    private static final long REACH_ID = 710039498L;

    @Mock
    private DispositivoPushRepository dispositivoRepository;

    @Mock
    private RioRepository rioRepository;

    @Mock
    private AlertaEventoRepository eventoRepository;

    @Mock
    private PushSender pushSender;

    private PushService service;

    @BeforeEach
    void preparar() {
        service = new PushService(
                dispositivoRepository, rioRepository, eventoRepository,
                pushSender, new PropiedadesFirebase(), REACH_ID);
        when(eventoRepository.save(any(AlertaEvento.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
        when(dispositivoRepository.save(any(DispositivoPush.class)))
                .thenAnswer(invocacion -> invocacion.getArgument(0));
    }

    @Test
    void sinDispositivosElEventoQuedaMarcadoYNoSeLlamaAFcm() {
        when(dispositivoRepository.findByReachIdAndActivoTrue(REACH_ID)).thenReturn(List.of());

        AlertaEvento evento = evento();
        service.enviarParaEvento(evento);

        assertEquals(EstadoEnvio.SIN_DISPOSITIVOS, evento.getEstadoEnvio());
        assertEquals(0, evento.getDispositivosObjetivo());
        assertNotNull(evento.getFechaIntentoEnvio());
        verify(pushSender, never()).enviar(any(), anyString());
    }

    @Test
    void fcmDeshabilitadoSeRegistraEnElEventoSinPropagarLaExcepcion() {
        when(dispositivoRepository.findByReachIdAndActivoTrue(REACH_ID))
                .thenReturn(List.of(dispositivo("token-1", "device-1", true)));
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
    void envioCorrectoMarcaElEventoComoEnviado() {
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true);
        when(dispositivoRepository.findByReachIdAndActivoTrue(REACH_ID)).thenReturn(List.of(dispositivo));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.ok());

        AlertaEvento evento = evento();
        service.enviarParaEvento(evento);

        assertEquals(EstadoEnvio.ENVIADA, evento.getEstadoEnvio());
        assertEquals(1, evento.getDispositivosEnviados());
        assertEquals(0, evento.getDispositivosFallidos());
        assertNotNull(evento.getFechaEnvio());
        verify(dispositivoRepository, never()).save(dispositivo);
    }

    @Test
    void tokenInvalidoSeDesactivaYElEnvioQuedaParcial() {
        DispositivoPush valido = dispositivo("token-1", "device-1", true);
        DispositivoPush invalido = dispositivo("token-2", "device-2", true);
        when(dispositivoRepository.findByReachIdAndActivoTrue(REACH_ID))
                .thenReturn(List.of(valido, invalido));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.ok());
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-2")))
                .thenReturn(ResultadoEnvioPush.tokenInvalido("Token no registrado."));

        AlertaEvento evento = evento();
        service.enviarParaEvento(evento);

        assertEquals(EstadoEnvio.PARCIAL, evento.getEstadoEnvio());
        assertEquals(1, evento.getDispositivosEnviados());
        assertEquals(1, evento.getDispositivosFallidos());
        assertTrue(evento.getErrorEnvio().contains("Token no registrado"));
        assertFalse(invalido.isActivo());
        verify(dispositivoRepository).save(invalido);
    }

    @Test
    void todosLosEnviosFallidosDejanElEventoEnFallida() {
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true);
        when(dispositivoRepository.findByReachIdAndActivoTrue(REACH_ID)).thenReturn(List.of(dispositivo));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.fallo("Servicio no disponible."));

        AlertaEvento evento = evento();
        service.enviarParaEvento(evento);

        assertEquals(EstadoEnvio.FALLIDA, evento.getEstadoEnvio());
        assertEquals(0, evento.getDispositivosEnviados());
        assertEquals(1, evento.getDispositivosFallidos());
        assertTrue(evento.getErrorEnvio().contains("Servicio no disponible"));
    }

    @Test
    void pruebaConFcmDeshabilitadoNoIntentaEnviar() {
        when(dispositivoRepository.findByToken("token-1"))
                .thenReturn(Optional.of(dispositivo("token-1", "device-1", true)));
        when(pushSender.estaHabilitado()).thenReturn(false);
        when(pushSender.motivoDeshabilitado()).thenReturn("FCM no configurado.");

        PruebaPushRequest solicitud = new PruebaPushRequest();
        solicitud.setToken("token-1");
        solicitud.setDeviceId("device-1");

        assertThrows(IllegalStateException.class, () -> service.enviarPrueba(solicitud));
        verify(pushSender, never()).enviar(any(), anyString());
    }

    @Test
    void pruebaTieneLimiteDeSesentaSegundos() {
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true);
        when(dispositivoRepository.findByToken("token-1")).thenReturn(Optional.of(dispositivo));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.ok());

        PruebaPushRequest solicitud = new PruebaPushRequest();
        solicitud.setToken("token-1");
        solicitud.setDeviceId("device-1");

        assertTrue(service.enviarPrueba(solicitud).exito());
        assertThrows(LimitePruebaExcedidoException.class, () -> service.enviarPrueba(solicitud));
    }

    @Test
    void pruebaConTokenInvalidoDesactivaElDispositivo() {
        DispositivoPush dispositivo = dispositivo("token-1", "device-1", true);
        when(dispositivoRepository.findByToken("token-1")).thenReturn(Optional.of(dispositivo));
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-1")))
                .thenReturn(ResultadoEnvioPush.tokenInvalido("Token inválido."));

        PruebaPushRequest solicitud = new PruebaPushRequest();
        solicitud.setToken("token-1");
        solicitud.setDeviceId("device-1");

        ResultadoEnvioPush resultado = service.enviarPrueba(solicitud);

        assertTrue(resultado.tokenInvalido());
        assertFalse(dispositivo.isActivo());
        verify(dispositivoRepository).save(dispositivo);
    }

    @Test
    void registrarUnTokenExistenteDelMismoDispositivoActualizaElTramo() {
        when(rioRepository.findFirstByGeoglowsId(REACH_ID))
                .thenReturn(Optional.of(new Rio("Río de prueba", REACH_ID, 9.08, -79.28)));
        DispositivoPush existente = dispositivo("token-1", "device-1", true);
        when(dispositivoRepository.findByToken("token-1")).thenReturn(Optional.of(existente));

        RegistroPushRequest solicitud = solicitud("token-1", "device-1", REACH_ID);
        RegistroPushResponse respuesta = service.registrar(solicitud);

        assertTrue(respuesta.activo());
        assertEquals(REACH_ID, existente.getReachId());
        assertEquals("web", existente.getPlataforma());
        verify(dispositivoRepository).save(existente);
    }

    @Test
    void registrarRechazaUnTokenYaAsociadoAOtroDispositivo() {
        when(rioRepository.findFirstByGeoglowsId(REACH_ID))
                .thenReturn(Optional.of(new Rio("Río de prueba", REACH_ID, 9.08, -79.28)));
        when(dispositivoRepository.findByToken("token-1"))
                .thenReturn(Optional.of(dispositivo("token-1", "otro-device", true)));

        RegistroPushRequest solicitud = solicitud("token-1", "device-1", REACH_ID);

        assertThrows(IllegalStateException.class, () -> service.registrar(solicitud));
    }

    @Test
    void registrarUnTokenNuevoDesactivaLosTokensAnterioresDelMismoDispositivo() {
        when(rioRepository.findFirstByGeoglowsId(REACH_ID))
                .thenReturn(Optional.of(new Rio("Río de prueba", REACH_ID, 9.08, -79.28)));
        when(dispositivoRepository.findByToken("token-nuevo")).thenReturn(Optional.empty());
        DispositivoPush anterior = dispositivo("token-viejo", "device-1", true);
        when(dispositivoRepository.findByDeviceIdAndActivoTrue("device-1"))
                .thenReturn(List.of(anterior));

        RegistroPushResponse respuesta = service.registrar(
                solicitud("token-nuevo", "device-1", REACH_ID));

        assertTrue(respuesta.activo());
        assertFalse(anterior.isActivo());
        verify(dispositivoRepository).save(anterior);
    }

    @Test
    void desactivarRechazaUnTokenDeOtroDispositivo() {
        when(dispositivoRepository.findByToken("token-1"))
                .thenReturn(Optional.of(dispositivo("token-1", "device-1", true)));

        assertThrows(IllegalStateException.class,
                () -> service.desactivar("token-1", "device-otro"));
    }

    @Test
    void demoEnviaATodosLosDispositivosActivos() {
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(dispositivoRepository.findByActivoTrue()).thenReturn(List.of(
                dispositivo("token-1", "device-1", true),
                dispositivo("token-2", "device-2", true)));
        when(pushSender.enviar(any(NotificacionPush.class), anyString()))
                .thenReturn(ResultadoEnvioPush.ok());

        ResultadoDemoPush resultado = service.enviarDemoATodos();

        assertEquals(2, resultado.dispositivos());
        assertEquals(2, resultado.enviados());
        assertEquals(0, resultado.fallidos());
        verify(pushSender).enviar(any(NotificacionPush.class), eq("token-1"));
        verify(pushSender).enviar(any(NotificacionPush.class), eq("token-2"));
    }

    @Test
    void demoDesactivaUnTokenInvalidoYSigueConLosDemas() {
        DispositivoPush invalido = dispositivo("token-malo", "device-1", true);
        DispositivoPush valido = dispositivo("token-2", "device-2", true);
        when(pushSender.estaHabilitado()).thenReturn(true);
        when(dispositivoRepository.findByActivoTrue()).thenReturn(List.of(invalido, valido));
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-malo")))
                .thenReturn(ResultadoEnvioPush.tokenInvalido("token inválido"));
        when(pushSender.enviar(any(NotificacionPush.class), eq("token-2")))
                .thenReturn(ResultadoEnvioPush.ok());

        ResultadoDemoPush resultado = service.enviarDemoATodos();

        assertEquals(1, resultado.enviados());
        assertEquals(1, resultado.fallidos());
        assertFalse(invalido.isActivo());
        assertTrue(valido.isActivo());
    }

    @Test
    void demoConFcmDeshabilitadoLanzaErrorClaro() {
        when(pushSender.estaHabilitado()).thenReturn(false);
        when(pushSender.motivoDeshabilitado()).thenReturn("Faltan credenciales de Firebase.");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.enviarDemoATodos());
        assertTrue(error.getMessage().contains("credenciales"));
        verify(pushSender, never()).enviar(any(), anyString());
    }

    private RegistroPushRequest solicitud(String token, String deviceId, long reachId) {
        RegistroPushRequest solicitud = new RegistroPushRequest();
        solicitud.setToken(token);
        solicitud.setDeviceId(deviceId);
        solicitud.setReachId(reachId);
        solicitud.setPlataforma("web");
        return solicitud;
    }

    private DispositivoPush dispositivo(String token, String deviceId, boolean activo) {
        DispositivoPush dispositivo = new DispositivoPush();
        dispositivo.setId(1L);
        dispositivo.setToken(token);
        dispositivo.setDeviceId(deviceId);
        dispositivo.setReachId(REACH_ID);
        dispositivo.setActivo(activo);
        dispositivo.setPlataforma("web");
        dispositivo.setFechaRegistro(Instant.now());
        dispositivo.setFechaActualizacion(Instant.now());
        return dispositivo;
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
