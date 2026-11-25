package com.alerta.sistema_alerta_inundaciones.rio;

public record CrearMuestraRequest(
        String fecha,
        String parametro,
        Double valor,
        String unidad,
        String nota
) {
}
