package com.alerta.sistema_alerta_inundaciones.usuario;

import com.alerta.sistema_alerta_inundaciones.auth.UsuarioDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    private AdminUsuarioService service;

    @BeforeEach
    void preparar() {
        service = new AdminUsuarioService(usuarioRepository);
    }

    @Test
    void cambiarRolRechazaUsuarioObjetivoAdmin() {
        Usuario admin = new Usuario("admin", "hash", Rol.ADMIN);
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(admin));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.cambiarRol(1L, new CambiarRolRequest("USER")));

        assertEquals(400, error.getStatusCode().value());
        assertEquals("No se puede cambiar el rol de un administrador.", error.getReason());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRolRechazaRolDestinoInvalido() {
        ResponseStatusException errorFoo = assertThrows(ResponseStatusException.class,
                () -> service.cambiarRol(2L, new CambiarRolRequest("FOO")));
        ResponseStatusException errorAdmin = assertThrows(ResponseStatusException.class,
                () -> service.cambiarRol(2L, new CambiarRolRequest("ADMIN")));

        assertEquals(400, errorFoo.getStatusCode().value());
        assertEquals(400, errorAdmin.getStatusCode().value());
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void cambiarRolAInvestigadorActualizaElRol() {
        Usuario usuario = new Usuario("investigador", "hash", Rol.USER);
        when(usuarioRepository.findById(3L)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        UsuarioDTO actualizado = service.cambiarRol(3L, new CambiarRolRequest("INVESTIGADOR"));

        assertEquals("INVESTIGADOR", actualizado.rol());
        assertEquals(Rol.INVESTIGADOR, usuario.getRol());
        verify(usuarioRepository).save(usuario);
    }
}
