package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;

public record UsuarioDTO(Long id, String identificador, String rol) {

    public static UsuarioDTO desde(Usuario usuario) {
        return new UsuarioDTO(usuario.getId(), usuario.getIdentificador(), usuario.getRol().name());
    }
}
