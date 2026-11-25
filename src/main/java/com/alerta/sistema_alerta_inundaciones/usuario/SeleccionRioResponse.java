package com.alerta.sistema_alerta_inundaciones.usuario;

/**
 * Respuesta devuelta al agregar un río a la lista de seguimiento de alertas del usuario.
 *
 * @param rioId   identificador del río seleccionado
 * @param seguido indica si el río quedó en estado seguido ({@code true})
 * @param mensaje mensaje informativo sobre el resultado de la operación
 */
public record SeleccionRioResponse(Long rioId, boolean seguido, String mensaje) {
}
