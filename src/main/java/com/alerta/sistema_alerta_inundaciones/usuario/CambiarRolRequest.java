package com.alerta.sistema_alerta_inundaciones.usuario;

/**
 * Cuerpo de solicitud para cambiar el rol de una cuenta de usuario.
 *
 * @param rol nombre del rol destino (por ejemplo, {@code USER} o {@code INVESTIGADOR})
 */
public record CambiarRolRequest(String rol) {
}
