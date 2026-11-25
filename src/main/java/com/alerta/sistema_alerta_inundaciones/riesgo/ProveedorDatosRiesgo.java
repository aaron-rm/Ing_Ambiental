package com.alerta.sistema_alerta_inundaciones.riesgo;

import java.io.IOException;

public interface ProveedorDatosRiesgo {

    DatosRiesgoTramo obtener(long reachId, String nombreRio, Double latitud, Double longitud)
            throws IOException, InterruptedException;
}
