package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.Rol;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * Servicio de negocio encargado del registro de usuarios, validación de
 * credenciales en el inicio de sesión y emisión de respuestas con JWT.
 */
@Service
public class AuthService {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern NOMBRE_USUARIO = Pattern.compile("^[a-z0-9._-]{3,50}$");
    private static final String MENSAJE_CREDENCIALES = "Identificador o contraseña incorrectos.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Crea el servicio de autenticación.
     *
     * @param usuarioRepository repositorio de usuarios
     * @param passwordEncoder   codificador de contraseñas
     * @param jwtService        servicio para firmar y validar tokens JWT
     */
    public AuthService(UsuarioRepository usuarioRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Valida el identificador, registra un nuevo usuario con rol {@link Rol#USER}
     * y genera su token JWT inicial.
     *
     * @param solicitud credenciales de registro
     * @return respuesta con el token JWT y el usuario creado
     * @throws IllegalArgumentException        si el identificador no tiene un formato válido
     * @throws IdentificadorDuplicadoException si el identificador ya está registrado
     */
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

    /**
     * Verifica las credenciales del usuario y emite un token JWT si son válidas.
     *
     * @param solicitud credenciales de inicio de sesión
     * @return respuesta con el token JWT y los datos del usuario
     * @throws CredencialesInvalidasException si el usuario no existe o la contraseña no coincide
     */
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

    /**
     * Busca un usuario por su identificador normalizado.
     *
     * @param identificador correo o nombre de usuario
     * @return entidad del usuario encontrado
     * @throws CredencialesInvalidasException si no existe una cuenta con ese identificador
     */
    @Transactional(readOnly = true)
    public Usuario obtenerPorIdentificador(String identificador) {
        return usuarioRepository.findByIdentificador(Usuario.normalizar(identificador))
                .orElseThrow(() -> new CredencialesInvalidasException("La sesión ya no es válida."));
    }

    // Construye la respuesta de autenticación con un nuevo token JWT y el DTO del usuario.
    private AuthResponse respuesta(Usuario usuario) {
        return new AuthResponse(jwtService.generarToken(usuario), UsuarioDTO.desde(usuario));
    }

    // Normaliza el identificador a minúsculas y verifica que cumpla el formato de correo o usuario.
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
