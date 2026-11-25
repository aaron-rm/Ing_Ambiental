package com.alerta.sistema_alerta_inundaciones.clima;

/**
 * Contrato de la fuente de pronóstico de precipitación usada por el sistema de
 * alertas de inundaciones para complementar el caudal con la lluvia prevista.
 */
public interface ProveedorPrecipitacion {

    /**
     * Obtiene el pronóstico de precipitación para una coordenada.
     * No lanza excepciones: si la fuente falla devuelve {@link DatosPrecipitacion#noDisponible}.
     *
     * @param latitud  latitud en grados
     * @param longitud longitud en grados
     * @return datos de precipitación, o un resultado no disponible si la fuente falla
     */
    DatosPrecipitacion obtener(double latitud, double longitud);
}
