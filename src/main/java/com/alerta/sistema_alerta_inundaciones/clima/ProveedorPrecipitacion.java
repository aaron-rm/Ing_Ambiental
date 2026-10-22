package com.alerta.sistema_alerta_inundaciones.clima;

public interface ProveedorPrecipitacion {

    /**
     * Obtiene el pronóstico de precipitación para una coordenada.
     * No lanza excepciones: si la fuente falla devuelve {@link DatosPrecipitacion#noDisponible}.
     */
    DatosPrecipitacion obtener(double latitud, double longitud);
}
