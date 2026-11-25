package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEvento;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NotificacionFactoryTest {

    private static final Instant FECHA = Instant.parse("2026-10-21T12:00:00Z");

    @Test
    void emergenciaGeneraNotificacionCriticaConDeepLinkYEtiqueta() {
        AlertaEvento evento = evento(NivelAlerta.EMERGENCIA, NivelAlerta.ALERTA);

        NotificacionPush notificacion = NotificacionFactory.paraEvento(evento);

        assertEquals("🚨 ALERTA DE INUNDACIÓN 🌊", notificacion.titulo());
        assertTrue(notificacion.cuerpo().contains("no oficial"));
        assertTrue(notificacion.cuerpo().contains("Río Pacora"));
        assertTrue(notificacion.cuerpo().contains("desbordamiento inminente"));
        assertTrue(notificacion.cuerpo().contains("75.5"));
        assertTrue(notificacion.cuerpo().contains("60.2"));
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

        assertEquals("⚠️ AVISO DE CRECIDA: Río Pacora en riesgo alto 🌧️",
                notificacion.titulo());
        assertTrue(notificacion.cuerpo().contains("riesgo alto"));
        assertTrue(notificacion.vibrar());
        assertFalse(notificacion.critico());
        assertFalse(notificacion.requiereInteraccion());
        assertEquals("/?alerta=5", notificacion.url());
    }

    @Test
    void recuperacionSoloSeAnunciaSiElNivelAnteriorEraAlto() {
        NotificacionPush recuperacion = NotificacionFactory.paraEvento(
                evento(NivelAlerta.NORMAL, NivelAlerta.EMERGENCIA));
        assertEquals("✅ Situación normalizada en Río Pacora", recuperacion.titulo());
        assertTrue(recuperacion.cuerpo().contains("volvió a la normalidad"));
        assertFalse(recuperacion.critico());
        assertFalse(recuperacion.vibrar());

        NotificacionPush normal = NotificacionFactory.paraEvento(
                evento(NivelAlerta.NORMAL, NivelAlerta.NORMAL));
        assertEquals("✅ Sin alerta de inundación en Río Pacora", normal.titulo());
    }

    @Test
    void vigilanciaTieneTituloInformativo() {
        NotificacionPush notificacion = NotificacionFactory.paraEvento(
                evento(NivelAlerta.VIGILANCIA, NivelAlerta.NORMAL));
        assertEquals("🌧️ Vigilancia por lluvias en Río Pacora", notificacion.titulo());
        assertFalse(notificacion.critico());
    }

    @Test
    void datosDelMensajeContienenTodoLoNecesarioParaElServiceWorker() {
        Map<String, String> datos = NotificacionFactory.paraEvento(
                evento(NivelAlerta.EMERGENCIA, NivelAlerta.ALERTA)).datos();

        assertEquals("🚨 ALERTA DE INUNDACIÓN 🌊", datos.get("title"));
        assertEquals("EMERGENCIA", datos.get("nivel"));
        assertEquals("710039498", datos.get("reachId"));
        assertEquals("5", datos.get("eventoId"));
        assertEquals("/?alerta=5", datos.get("url"));
        assertEquals("alerta-inundacion-710039498", datos.get("tag"));
        assertEquals("true", datos.get("critico"));
        assertEquals("true", datos.get("vibrar"));
        assertEquals(String.valueOf(FECHA.toEpochMilli()), datos.get("timestamp"));
    }

    @Test
    void simulacionUsaElMismoTextoVisibleQueUnaAlertaRealSinDecirSimulacro() {
        AlertaRioDTO alerta = simulacion(NivelAlerta.EMERGENCIA);

        NotificacionPush notificacion = NotificacionFactory.paraSimulacion(alerta);

        assertEquals("🚨 ALERTA DE INUNDACIÓN 🌊", notificacion.titulo());
        assertTrue(notificacion.cuerpo().contains("Río Abajo"));
        assertTrue(notificacion.cuerpo().contains("desbordamiento inminente"));
        assertTrue(notificacion.cuerpo().contains("75.0"));
        assertTrue(notificacion.cuerpo().contains("no oficial"));
        assertFalse(contieneTextoDePrueba(notificacion));
        assertEquals("EMERGENCIA", notificacion.nivel());
        assertEquals(710039498L, notificacion.reachId());
        assertEquals(0L, notificacion.eventoId());
        assertEquals("/?simulacion=710039498", notificacion.url());
        assertEquals("simulacion-710039498", notificacion.tag());
        assertTrue(notificacion.critico());
        assertTrue(notificacion.requiereInteraccion());
        assertTrue(notificacion.vibrar());
        assertEquals(FECHA.toEpochMilli(), notificacion.timestamp());
    }

    @Test
    void simulacionDeAlertaUsaElAvisoDeCrecidaYNoEsCritica() {
        AlertaRioDTO alerta = simulacion(NivelAlerta.ALERTA);

        NotificacionPush notificacion = NotificacionFactory.paraSimulacion(alerta);

        assertEquals("⚠️ AVISO DE CRECIDA: Río Abajo en riesgo alto 🌧️",
                notificacion.titulo());
        assertTrue(notificacion.vibrar());
        assertFalse(notificacion.critico());
        assertFalse(notificacion.requiereInteraccion());
        assertFalse(contieneTextoDePrueba(notificacion));
    }

    @Test
    void demoGlobalUsaTextoDeEmergenciaRealSinMarcasDePrueba() {
        NotificacionPush notificacion = NotificacionFactory.paraDemoGlobal();

        assertEquals("🚨 ALERTA DE INUNDACIÓN 🌊", notificacion.titulo());
        assertTrue(notificacion.cuerpo().contains("ríos monitoreados"));
        assertTrue(notificacion.cuerpo().contains("no oficial"));
        assertFalse(contieneTextoDePrueba(notificacion));
        assertEquals("EMERGENCIA", notificacion.nivel());
        assertEquals("/?demo=1", notificacion.url());
        assertEquals("demo-push", notificacion.tag());
        assertTrue(notificacion.critico());
        assertTrue(notificacion.requiereInteraccion());
        assertTrue(notificacion.vibrar());
    }

    private boolean contieneTextoDePrueba(NotificacionPush notificacion) {
        String texto = (notificacion.titulo() + " " + notificacion.cuerpo())
                .toLowerCase(Locale.ROOT);
        return texto.contains("simulacro") || texto.contains("simulación")
                || texto.contains("simulacion") || texto.contains("prueba")
                || texto.contains("demostración") || texto.contains("demostracion");
    }

    private AlertaRioDTO simulacion(NivelAlerta nivel) {
        AlertaRioDTO alerta = new AlertaRioDTO();
        alerta.setReachId(710039498L);
        alerta.setNombreRio("Río Abajo");
        alerta.setNivelAlerta(nivel.name());
        alerta.setCaudalMaximoPronosticado(75.0);
        alerta.setFechaEvaluacion(FECHA);
        return alerta;
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
