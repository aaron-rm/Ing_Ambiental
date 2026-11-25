package com.alerta.sistema_alerta_inundaciones.riesgo;

import java.io.IOException;

/**
 * Contrato de la fuente que reúne los datos necesarios para evaluar el riesgo
 * de inundación de un tramo (caudal, umbrales y precipitación).
 */
public interface ProveedorDatosRiesgo {

    /**
     * Obtiene los datos de un tramo para evaluar su riesgo.
     *
     * @param reachId   identificador del tramo en GEOGloWS
     * @param nombreRio nombre del río; si es vacío se usa un nombre genérico
     * @param latitud   latitud del río, o {@code null} si no está configurada
     * @param longitud  longitud del río, o {@code null} si no está configurada
     * @return instantánea de datos del tramo
     * @throws IOException          si la fuente de caudal falla o devuelve datos inutilizables
     * @throws InterruptedException si la consulta es interrumpida
     */
    DatosRiesgoTramo obtener(long reachId, String nombreRio, Double latitud, Double longitud)
            throws IOException, InterruptedException;
}
