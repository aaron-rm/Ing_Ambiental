package com.alerta.sistema_alerta_inundaciones.push;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Contenido de una notificación web. Los valores viajan como data-only para
 * que el Service Worker controle la presentación y el clic.
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
