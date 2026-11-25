package com.alerta.sistema_alerta_inundaciones.usuario;

import com.alerta.sistema_alerta_inundaciones.auth.UsuarioDTO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;

/**
 * Servicio de negocio para que los administradores consulten la lista de cuentas
 * y modifiquen el rol de usuarios entre {@link Rol#USER} e {@link Rol#INVESTIGADOR}.
 */
@Service
public class AdminUsuarioService {

    private final UsuarioRepository usuarioRepository;

    /**
     * Crea el servicio administrativo de usuarios.
     *
     * @param usuarioRepository repositorio de usuarios
     */
    public AdminUsuarioService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Devuelve todos los usuarios registrados ordenados por identificador ascendente.
     *
     * @return lista de {@link UsuarioDTO}
     */
    @Transactional(readOnly = true)
    public List<UsuarioDTO> listarUsuarios() {
        return usuarioRepository.findAllByOrderByIdentificadorAsc().stream()
                .map(UsuarioDTO::desde)
                .toList();
    }

    /**
     * Cambia el rol de un usuario no administrador a {@link Rol#USER} o {@link Rol#INVESTIGADOR}.
     *
     * @param id        identificador del usuario a actualizar
     * @param solicitud cuerpo con el nombre del nuevo rol
     * @return DTO del usuario con el rol actualizado
     * @throws ResponseStatusException con estado 400 si el rol es inválido o el objetivo es ADMIN,
     *                                 o 404 si el usuario no existe
     */
    @Transactional
    public UsuarioDTO cambiarRol(Long id, CambiarRolRequest solicitud) {
        String rolTexto = solicitud == null || solicitud.rol() == null
                ? ""
                : solicitud.rol().trim().toUpperCase(Locale.ROOT);
        Rol nuevoRol;
        if ("USER".equals(rolTexto)) {
            nuevoRol = Rol.USER;
        } else if ("INVESTIGADOR".equals(rolTexto)) {
            nuevoRol = Rol.INVESTIGADOR;
        } else {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Rol inválido. Solo se admite USER o INVESTIGADOR.");
        }

        Usuario usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "No existe un usuario con ID " + id + "."));

        if (usuario.getRol() == Rol.ADMIN) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No se puede cambiar el rol de un administrador.");
        }

        usuario.setRol(nuevoRol);
        return UsuarioDTO.desde(usuarioRepository.save(usuario));
    }
}
