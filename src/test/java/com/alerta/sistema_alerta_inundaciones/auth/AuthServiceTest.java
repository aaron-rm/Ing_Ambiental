package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.Rol;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceTest {

    private UsuarioRepository usuarioRepository;
    private PasswordEncoder passwordEncoder;
    private JwtService jwtService;
    private AuthService authService;

    @BeforeEach
    void preparar() {
        usuarioRepository = mock(UsuarioRepository.class);
        passwordEncoder = new BCryptPasswordEncoder();
        jwtService = new JwtService("secreto-de-prueba-con-mas-de-32-caracteres-123456", 24);
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService);
    }

    @Test
    void registroCreaUsuarioNormalYHasheaLaContrasena() {
        when(usuarioRepository.existsByIdentificador("juan")).thenReturn(false);
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        AuthResponse respuesta = authService.registrar(new CredencialesRequest("Juan", "clave-segura-123"));

        assertEquals("juan", respuesta.usuario().identificador());
        assertEquals("USER", respuesta.usuario().rol());
        assertFalse(respuesta.token().isBlank());
        verify(usuarioRepository).save(any(Usuario.class));
    }

    @Test
    void registroRechazaIdentificadorDuplicado() {
        when(usuarioRepository.existsByIdentificador("juan@correo.com")).thenReturn(true);

        assertThrows(IdentificadorDuplicadoException.class,
                () -> authService.registrar(new CredencialesRequest("JUAN@correo.com", "clave-segura-123")));
    }

    @Test
    void registroRechazaIdentificadorInvalido() {
        assertThrows(IllegalArgumentException.class,
                () -> authService.registrar(new CredencialesRequest("con espacios", "clave-segura-123")));
    }

    @Test
    void loginAceptaMayusculasYMinusculasYDevuelveToken() {
        Usuario usuario = new Usuario("juan@correo.com",
                passwordEncoder.encode("clave-segura-123"), Rol.USER);
        when(usuarioRepository.findByIdentificador("juan@correo.com")).thenReturn(Optional.of(usuario));

        AuthResponse respuesta = authService.login(new CredencialesRequest("JUAN@CORREO.COM", "clave-segura-123"));

        assertEquals("juan@correo.com", respuesta.usuario().identificador());
        assertEquals("juan@correo.com", jwtService.identificadorDe(respuesta.token()));
    }

    @Test
    void loginRechazaContrasenaIncorrectaConMensajeGenerico() {
        Usuario usuario = new Usuario("juan", passwordEncoder.encode("clave-segura-123"), Rol.USER);
        when(usuarioRepository.findByIdentificador("juan")).thenReturn(Optional.of(usuario));

        CredencialesInvalidasException error = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new CredencialesRequest("juan", "otra-clave-123")));

        assertEquals("Identificador o contraseña incorrectos.", error.getMessage());
    }

    @Test
    void loginRechazaIdentificadorInexistenteConElMismoMensaje() {
        when(usuarioRepository.findByIdentificador("nadie")).thenReturn(Optional.empty());

        CredencialesInvalidasException error = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new CredencialesRequest("nadie", "clave-segura-123")));

        assertTrue(error.getMessage().contains("incorrectos"));
    }
}
