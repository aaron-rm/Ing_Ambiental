package com.alerta.sistema_alerta_inundaciones.push;

import com.alerta.sistema_alerta_inundaciones.GEOGLOWS.LectorDatosIngresados.NivelAlerta;
import com.alerta.sistema_alerta_inundaciones.alerta.AlertaEvento;

import java.util.Locale;

/** Construye el contenido dinámico de las notificaciones a partir del evento. */
public final class NotificacionFactory {

    private static final String ICONO = "/icons/icon-192.png";
    private static final String BADGE = "/icons/badge-72.png";

    private NotificacionFactory() {
    }

    public static NotificacionPush paraEvento(AlertaEvento evento) {
        boolean recuperacion = evento.getNivel() == NivelAlerta.NORMAL
                && evento.getNivelAnterior() != null
                && evento.getNivelAnterior().ordinal() >= NivelAlerta.ALERTA.ordinal();
        boolean critico = evento.getNivel() == NivelAlerta.EMERGENCIA;

        String titulo;
        if (critico) {
            titulo = "ALERTA DE INUNDACIÓN";
        } else if (evento.getNivel() == NivelAlerta.ALERTA) {
            titulo = "Riesgo de inundación";
        } else if (recuperacion) {
            titulo = "Situación normalizada";
        } else if (evento.getNivel() == NivelAlerta.VIGILANCIA) {
            titulo = "Vigilancia por lluvias";
        } else {
            titulo = "Sin alerta de inundación";
        }

        String cuerpo = construirCuerpo(evento, critico, recuperacion);
        String url = "/?alerta=" + evento.getId();
        String tag = "alerta-inundacion-" + evento.getReachId();

        return new NotificacionPush(
                titulo,
                cuerpo,
                evento.getNivel().name(),
                evento.getReachId(),
                evento.getId() == null ? 0L : evento.getId(),
                url,
                tag,
                ICONO,
                BADGE,
                critico,
                critico,
                critico || evento.getNivel() == NivelAlerta.ALERTA,
                evento.getFechaCreacion() == null ? System.currentTimeMillis() : evento.getFechaCreacion().toEpochMilli()
        );
    }

    private static String construirCuerpo(AlertaEvento evento, boolean critico, boolean recuperacion) {
        String rio = evento.getNombreRio() == null || evento.getNombreRio().isBlank()
                ? "tu zona" : evento.getNombreRio();
        String caudal = String.format(Locale.US, "%.1f", evento.getCaudalMaximo());
        String lluvia = evento.getLluvia24hMm() == null
                ? null : String.format(Locale.US, "%.1f", evento.getLluvia24hMm());

        if (recuperacion) {
            return "El nivel de riesgo en " + rio + " volvió a la normalidad. "
                    + "Los datos pueden cambiar; consulta la aplicación para ver la actualización.";
        }
        if (critico) {
            String base = "Nivel crítico detectado por el sistema comunitario (no oficial) en " + rio
                    + ". Caudal previsto " + caudal + " m³/s";
            if (lluvia != null) {
                base += "; lluvia prevista en 24 h " + lluvia + " mm";
            }
            return base + ". Abre la aplicación para consultar la situación y las recomendaciones disponibles.";
        }
        if (evento.getNivel() == NivelAlerta.ALERTA) {
            String base = "Se ha detectado un nivel de riesgo alto en " + rio
                    + ". Caudal previsto " + caudal + " m³/s";
            if (lluvia != null) {
                base += "; lluvia prevista en 24 h " + lluvia + " mm";
            }
            return base + ". Abre la aplicación para consultar la situación y las recomendaciones disponibles.";
        }
        if (evento.getNivel() == NivelAlerta.VIGILANCIA) {
            return "Condiciones de vigilancia en " + rio + ". Caudal previsto " + caudal
                    + " m³/s. Consulta la aplicación para más detalles.";
        }
        return "El nivel de riesgo en " + rio + " es normal. Caudal previsto " + caudal + " m³/s.";
    }
}
