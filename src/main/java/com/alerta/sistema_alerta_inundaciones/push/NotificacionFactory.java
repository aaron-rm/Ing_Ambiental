package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEvento;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaRioDTO;

import java.util.Locale;

/**
 * Construye el contenido dinámico de las notificaciones.
 *
 * <p>Las plantillas usan emojis para que la alerta se distinga de una
 * notificación común. Las simulaciones administrativas reutilizan el mismo
 * texto visible que una alerta real; el carácter de simulación se conserva en
 * el payload (nivel y URL {@code /?simulacion=...}) y en la consola/PWA, no en
 * el texto que ve el dispositivo.</p>
 */
public final class NotificacionFactory {

    private static final String ICONO = "/icons/icon-192.png";
    private static final String BADGE = "/icons/badge-72.png";

    private NotificacionFactory() {
    }

    public static NotificacionPush paraEvento(AlertaEvento evento) {
        NivelAlerta nivel = evento.getNivel() == null ? NivelAlerta.NORMAL : evento.getNivel();
        boolean recuperacion = nivel == NivelAlerta.NORMAL
                && evento.getNivelAnterior() != null
                && evento.getNivelAnterior().ordinal() >= NivelAlerta.ALERTA.ordinal();

        return crear(
                nivel,
                evento.getNombreRio(),
                evento.getCaudalMaximo(),
                evento.getLluvia24hMm(),
                recuperacion,
                evento.getReachId(),
                evento.getId() == null ? 0L : evento.getId(),
                "/?alerta=" + evento.getId(),
                "alerta-inundacion-" + evento.getReachId(),
                evento.getFechaCreacion() == null
                        ? System.currentTimeMillis() : evento.getFechaCreacion().toEpochMilli()
        );
    }

    /**
     * Notificación de una crecida simulada. Usa el mismo texto visible que una
     * alerta real para la demostración en clase; la URL y el tag permiten
     * distinguirla internamente.
     */
    public static NotificacionPush paraSimulacion(AlertaRioDTO alerta) {
        NivelAlerta nivel = nivelDe(alerta.getNivelAlerta());

        return crear(
                nivel,
                alerta.getNombreRio(),
                alerta.getCaudalMaximoPronosticado(),
                alerta.getLluvia24hMm(),
                false,
                alerta.getReachId(),
                0L,
                "/?simulacion=" + alerta.getReachId(),
                "simulacion-" + alerta.getReachId(),
                alerta.getFechaEvaluacion() == null
                        ? System.currentTimeMillis() : alerta.getFechaEvaluacion().toEpochMilli()
        );
    }

    /**
     * Aviso genérico para la demostración global. No está asociado a un río;
     * usa el mismo lenguaje de una emergencia real.
     */
    public static NotificacionPush paraDemoGlobal() {
        return new NotificacionPush(
                "🚨 ALERTA DE INUNDACIÓN 🌊",
                "🔴 EMERGENCIA: crecida repentina detectada en los ríos monitoreados. "
                        + "Sistema comunitario (no oficial). Abre la aplicación para consultar "
                        + "la situación y las recomendaciones. ⚠️",
                NivelAlerta.EMERGENCIA.name(),
                0L,
                0L,
                "/?demo=1",
                "demo-push",
                ICONO,
                BADGE,
                true,
                true,
                true,
                System.currentTimeMillis()
        );
    }

    private static NotificacionPush crear(NivelAlerta nivel, String nombreRio,
                                           double caudal, Double lluvia24hMm,
                                           boolean recuperacion, Long reachId, long eventoId,
                                           String url, String tag, long timestamp) {
        boolean critico = nivel == NivelAlerta.EMERGENCIA;
        String rio = nombreRio == null || nombreRio.isBlank() ? "tu zona" : nombreRio;

        return new NotificacionPush(
                titulo(nivel, rio, recuperacion),
                cuerpo(nivel, rio, caudal, lluvia24hMm, recuperacion),
                nivel.name(),
                reachId == null ? 0L : reachId,
                eventoId,
                url,
                tag,
                ICONO,
                BADGE,
                critico,
                critico,
                critico || nivel == NivelAlerta.ALERTA,
                timestamp
        );
    }

    private static String titulo(NivelAlerta nivel, String rio, boolean recuperacion) {
        if (recuperacion) {
            return "✅ Situación normalizada en " + rio;
        }
        return switch (nivel) {
            case EMERGENCIA -> "🚨 ALERTA DE INUNDACIÓN 🌊";
            case ALERTA -> "⚠️ AVISO DE CRECIDA: " + rio + " en riesgo alto 🌧️";
            case VIGILANCIA -> "🌧️ Vigilancia por lluvias en " + rio;
            case NORMAL -> "✅ Sin alerta de inundación en " + rio;
        };
    }

    private static String cuerpo(NivelAlerta nivel, String rio, double caudal,
                                 Double lluvia24hMm, boolean recuperacion) {
        String caudalTexto = String.format(Locale.US, "%.1f", caudal);
        String lluviaTexto = lluvia24hMm == null
                ? null : String.format(Locale.US, "%.1f", lluvia24hMm);

        if (recuperacion) {
            return "✅ El nivel de riesgo en " + rio + " volvió a la normalidad. "
                    + "Los datos pueden cambiar; consulta la aplicación para ver la actualización.";
        }
        if (nivel == NivelAlerta.EMERGENCIA) {
            String base = "🔴 EMERGENCIA: desbordamiento inminente en " + rio
                    + ". Caudal previsto " + caudalTexto + " m³/s";
            if (lluviaTexto != null) {
                base += "; lluvia prevista en 24 h " + lluviaTexto + " mm";
            }
            return base + ". Sistema comunitario (no oficial). Abre la aplicación para "
                    + "consultar la situación y las recomendaciones. ⚠️";
        }
        if (nivel == NivelAlerta.ALERTA) {
            String base = "⚠️ " + rio + " está en riesgo alto. Caudal previsto "
                    + caudalTexto + " m³/s";
            if (lluviaTexto != null) {
                base += "; lluvia prevista en 24 h " + lluviaTexto + " mm";
            }
            return base + ". Abre la aplicación para consultar la situación y las "
                    + "recomendaciones. 🌧️";
        }
        if (nivel == NivelAlerta.VIGILANCIA) {
            return "🌧️ Condiciones de vigilancia en " + rio + ". Caudal previsto "
                    + caudalTexto + " m³/s. Consulta la aplicación para más detalles.";
        }
        return "✅ El nivel de riesgo en " + rio + " es normal. Caudal previsto "
                + caudalTexto + " m³/s.";
    }

    private static NivelAlerta nivelDe(String valor) {
        if (valor == null || valor.isBlank()) {
            return NivelAlerta.EMERGENCIA;
        }
        try {
            return NivelAlerta.valueOf(valor.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NivelAlerta.EMERGENCIA;
        }
    }
}
