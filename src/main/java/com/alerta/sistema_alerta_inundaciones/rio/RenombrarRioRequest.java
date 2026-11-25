package com.alerta.sistema_alerta_inundaciones.rio;

/**
 * Cuerpo de solicitud para cambiar el nombre visible de un río.
 *
 * @param nombre nuevo nombre del río (1 a 100 caracteres, sin contar espacios a los extremos)
 */
public record RenombrarRioRequest(String nombre) {
}
