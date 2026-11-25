package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;

/**
 * Representación pública de un usuario sin exponer su hash de contraseña.
 *
 * @param id            identificador numérico del usuario
 * @param identificador correo o nombre de usuario normalizado
 * @param rol           nombre del rol asignado a la cuenta
 */
public record UsuarioDTO(Long id, String identificador, String rol) {

    /**
     * Convierte una entidad {@link Usuario} en su representación DTO.
     *
     * @param usuario entidad de usuario
     * @return instancia de {@link UsuarioDTO} con los datos básicos del usuario
     */
    public static UsuarioDTO desde(Usuario usuario) {
        return new UsuarioDTO(usuario.getId(), usuario.getIdentificador(), usuario.getRol().name());
    }
}
