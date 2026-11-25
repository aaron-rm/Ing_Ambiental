package com.alerta.sistema_alerta_inundaciones.push;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contenido de una notificación web. Los valores viajan como data-only para
 * que el Service Worker controle la presentación y el clic.
 *
 * @param titulo título visible de la notificación
 * @param cuerpo texto visible de la notificación
 * @param nivel nivel de alerta asociado (NORMAL, VIGILANCIA, ALERTA, EMERGENCIA o PRUEBA)
 * @param reachId identificador del tramo de río (0 si no aplica)
 * @param eventoId identificador del evento de alerta (0 si no aplica)
 * @param url ruta que se abre al pulsar la notificación
 * @param tag etiqueta para agrupar/reemplazar notificaciones
 * @param icono ruta del icono
 * @param badge ruta del badge
 * @param critico {@code true} si es de alta prioridad (emergencia)
 * @param requiereInteraccion {@code true} si debe permanecer hasta que el usuario actúe
 * @param vibrar {@code true} si debe hacer vibrar el dispositivo
 * @param timestamp marca de tiempo en milisegundos desde epoch
 */
public record NotificacionPush(
        String titulo,
        String cuerpo,
        String nivel,
        long reachId,
        long eventoId,
        String url,
        String tag,
        String icono,
        String badge,
        boolean critico,
        boolean requiereInteraccion,
        boolean vibrar,
        long timestamp
) {

    /**
     * Convierte la notificación en el mapa de datos (todo como texto) que se envía por FCM.
     *
     * @return mapa clave-valor con los campos de la notificación
     */
    public Map<String, String> datos() {
        Map<String, String> datos = new LinkedHashMap<>();
        datos.put("title", titulo);
        datos.put("body", cuerpo);
        datos.put("nivel", nivel);
        datos.put("reachId", String.valueOf(reachId));
        datos.put("eventoId", String.valueOf(eventoId));
        datos.put("url", url);
        datos.put("tag", tag);
        datos.put("icon", icono);
        datos.put("badge", badge);
        datos.put("critico", String.valueOf(critico));
        datos.put("requiereInteraccion", String.valueOf(requiereInteraccion));
        datos.put("vibrar", String.valueOf(vibrar));
        datos.put("timestamp", String.valueOf(timestamp));
        return datos;
    }
}
