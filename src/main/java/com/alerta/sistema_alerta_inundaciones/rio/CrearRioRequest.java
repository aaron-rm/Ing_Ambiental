package com.alerta.sistema_alerta_inundaciones.rio;

/**
 * Cuerpo de solicitud para dar de alta un río a partir de su nombre y coordenadas geográficas.
 *
 * @param nombre   nombre visible del río
 * @param latitud  latitud en grados decimales (-90 a 90)
 * @param longitud longitud en grados decimales (-180 a 180)
 */
public record CrearRioRequest(String nombre, Double latitud, Double longitud) {
}
