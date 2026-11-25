package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.Rol;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern NOMBRE_USUARIO = Pattern.compile("^[a-z0-9._-]{3,50}$");
    private static final String MENSAJE_CREDENCIALES = "Identificador o contraseña incorrectos.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse registrar(CredencialesRequest solicitud) {
        String identificador = normalizarYValidar(solicitud.identificador());
        if (usuarioRepository.existsByIdentificador(identificador)) {
            throw new IdentificadorDuplicadoException("Ese identificador ya está registrado.");
        }

        Usuario usuario = new Usuario(
                identificador,
                passwordEncoder.encode(solicitud.password()),
                Rol.USER
        );
        try {
            usuarioRepository.save(usuario);
        } catch (DataIntegrityViolationException e) {
            throw new IdentificadorDuplicadoException("Ese identificador ya está registrado.");
        }
        return respuesta(usuario);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(CredencialesRequest solicitud) {
        String identificador = Usuario.normalizar(solicitud.identificador());
        Usuario usuario = usuarioRepository.findByIdentificador(identificador)
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_CREDENCIALES));

        if (!passwordEncoder.matches(solicitud.password(), usuario.getPasswordHash())) {
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES);
        }
        return respuesta(usuario);
    }

    @Transactional(readOnly = true)
    public Usuario obtenerPorIdentificador(String identificador) {
        return usuarioRepository.findByIdentificador(Usuario.normalizar(identificador))
                .orElseThrow(() -> new CredencialesInvalidasException("La sesión ya no es válida."));
    }

    private AuthResponse respuesta(Usuario usuario) {
        return new AuthResponse(jwtService.generarToken(usuario), UsuarioDTO.desde(usuario));
    }

    private String normalizarYValidar(String identificador) {
        String limpio = Usuario.normalizar(identificador);
        if (limpio == null || limpio.isBlank()) {
            throw new IllegalArgumentException("El identificador es obligatorio.");
        }
        boolean valido = limpio.contains("@")
                ? limpio.length() <= 80 && CORREO.matcher(limpio).matches()
                : NOMBRE_USUARIO.matcher(limpio).matches();
        if (!valido) {
            throw new IllegalArgumentException(limpio.contains("@")
                    ? "El correo no tiene un formato válido."
                    : "El nombre de usuario debe tener entre 3 y 50 caracteres y usar solo letras, números, punto, guion o guion bajo.");
        }
        return limpio;
    }
}
