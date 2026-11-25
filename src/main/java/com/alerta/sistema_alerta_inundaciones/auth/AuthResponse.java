package com.alerta.sistema_alerta_inundaciones.auth;

/**
 * Respuesta devuelta tras un registro o inicio de sesión exitoso,
 * con el token JWT emitido y los datos públicos del usuario.
 *
 * @param token   token JWT firmado para autenticar peticiones posteriores
 * @param usuario datos públicos de la cuenta autenticada
 */
public record AuthResponse(String token, UsuarioDTO usuario) {
}
