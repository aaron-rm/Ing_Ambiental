package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEvento;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificacionFactoryTest {

    private static final Instant FECHA = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void emergenciaGeneraNotificacionCriticaConDeepLinkYEtiqueta() {
        AlertaEvento evento = evento(NivelAlerta.EMERGENCIA, NivelAlerta.ALERTA);

        NotificacionPush notificacion = NotificacionFactory.paraEvento(evento);

        assertEquals("ALERTA DE INUNDACIÓN", notificacion.titulo());
        assertTrue(notificacion.cuerpo().contains("no oficial"));
        assertTrue(notificacion.cuerpo().contains("Río Pacora"));
        assertEquals("EMERGENCIA", notificacion.nivel());
        assertEquals(710039498L, notificacion.reachId());
        assertEquals(5L, notificacion.eventoId());
        assertEquals("/?alerta=5", notificacion.url());
        assertEquals("alerta-inundacion-710039498", notificacion.tag());
        assertEquals("/icons/icon-192.png", notificacion.icono());
        assertEquals("/icons/badge-72.png", notificacion.badge());
        assertTrue(notificacion.critico());
        assertTrue(notificacion.requiereInteraccion());
        assertTrue(notificacion.vibrar());
        assertEquals(FECHA.toEpochMilli(), notificacion.timestamp());
    }

    @Test
    void alertaNoEsCriticaPeroSiVibraYUsaElMismoDeepLink() {
        NotificacionPush notificacion = NotificacionFactory.paraEvento(
                evento(NivelAlerta.ALERTA, NivelAlerta.NORMAL));

        assertEquals("Riesgo de inundación", notificacion.titulo());
        assertTrue(notificacion.vibrar());
        assertTrue(!notificacion.critico());
        assertTrue(!notificacion.requiereInteraccion());
        assertEquals("/?alerta=5", notificacion.url());
    }

    @Test
    void recuperacionSoloSeAnunciaSiElNivelAnteriorEraAlto() {
        NotificacionPush recuperacion = NotificacionFactory.paraEvento(
                evento(NivelAlerta.NORMAL, NivelAlerta.EMERGENCIA));
        assertEquals("Situación normalizada", recuperacion.titulo());
        assertTrue(recuperacion.cuerpo().contains("volvió a la normalidad"));
        assertTrue(!recuperacion.critico());
        assertTrue(!recuperacion.vibrar());

        NotificacionPush normal = NotificacionFactory.paraEvento(
                evento(NivelAlerta.NORMAL, NivelAlerta.NORMAL));
        assertEquals("Sin alerta de inundación", normal.titulo());
    }

    @Test
    void vigilanciaTieneTituloInformativo() {
        NotificacionPush notificacion = NotificacionFactory.paraEvento(
                evento(NivelAlerta.VIGILANCIA, NivelAlerta.NORMAL));
        assertEquals("Vigilancia por lluvias", notificacion.titulo());
        assertTrue(!notificacion.critico());
    }

    @Test
    void datosDelMensajeContienenTodoLoNecesarioParaElServiceWorker() {
        Map<String, String> datos = NotificacionFactory.paraEvento(
                evento(NivelAlerta.EMERGENCIA, NivelAlerta.ALERTA)).datos();

        assertEquals("ALERTA DE INUNDACIÓN", datos.get("title"));
        assertEquals("EMERGENCIA", datos.get("nivel"));
        assertEquals("710039498", datos.get("reachId"));
        assertEquals("5", datos.get("eventoId"));
        assertEquals("/?alerta=5", datos.get("url"));
        assertEquals("alerta-inundacion-710039498", datos.get("tag"));
        assertEquals("true", datos.get("critico"));
        assertEquals("true", datos.get("vibrar"));
        assertEquals(String.valueOf(FECHA.toEpochMilli()), datos.get("timestamp"));
    }

    private AlertaEvento evento(NivelAlerta nivel, NivelAlerta anterior) {
        AlertaEvento evento = new AlertaEvento();
        evento.setId(5L);
        evento.setReachId(710039498L);
        evento.setNombreRio("Río Pacora");
        evento.setNivel(nivel);
        evento.setNivelAnterior(anterior);
        evento.setCaudalMaximo(75.5);
        evento.setLluvia24hMm(60.2);
        evento.setFechaCreacion(FECHA);
        return evento;
    }
}
