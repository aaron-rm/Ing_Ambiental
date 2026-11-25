package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controlador REST para el registro de cuentas, inicio de sesión y consulta
 * del perfil del usuario autenticado en el sistema.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    /**
     * Crea el controlador de autenticación.
     *
     * @param authService servicio de autenticación y registro
     */
    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * Registra una nueva cuenta de usuario con rol USER y devuelve su token JWT.
     * Endpoint: {@code POST /api/auth/registro}. Acceso: público.
     *
     * @param solicitud credenciales de registro (identificador y contraseña)
     * @return respuesta con estado 201, el token JWT y los datos del usuario
     */
    @PostMapping("/registro")
    public ResponseEntity<AuthResponse> registrar(@Valid @RequestBody CredencialesRequest solicitud) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.registrar(solicitud));
    }

    /**
     * Autentica a un usuario existente y devuelve un token JWT.
     * Endpoint: {@code POST /api/auth/login}. Acceso: público.
     *
     * @param solicitud credenciales de inicio de sesión
     * @return respuesta con el token JWT y los datos del usuario autenticado
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody CredencialesRequest solicitud) {
        return ResponseEntity.ok(authService.login(solicitud));
    }

    /**
     * Devuelve los datos del usuario correspondiente a la sesión actual.
     * Endpoint: {@code GET /api/auth/me}. Acceso: usuario con cuenta (la ruta es pública en
     * {@link SecurityConfig}, pero requiere un usuario autenticado para no responder 401).
     *
     * @param usuario usuario autenticado extraído del contexto de seguridad
     * @return datos del usuario actual o estado 401 si no hay sesión válida
     */
    @GetMapping("/me")
    public ResponseEntity<UsuarioDTO> sesionActual(@AuthenticationPrincipal Usuario usuario) {
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(UsuarioDTO.desde(usuario));
    }

    /**
     * Maneja el intento de registro con un identificador ya existente.
     *
     * @param e excepción de identificador duplicado
     * @return respuesta HTTP 409 con el mensaje de error
     */
    @ExceptionHandler(IdentificadorDuplicadoException.class)
    public ResponseEntity<String> duplicado(IdentificadorDuplicadoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }

    /**
     * Maneja fallos de autenticación por credenciales inválidas.
     *
     * @param e excepción de credenciales inválidas
     * @return respuesta HTTP 401 con el mensaje de error
     */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<String> credenciales(CredencialesInvalidasException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
    }

    /**
     * Maneja errores de validación de formato en los argumentos recibidos.
     *
     * @param e excepción de argumento inválido
     * @return respuesta HTTP 400 con el mensaje de error
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> datosInvalidos(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(e.getMessage());
    }

    /**
     * Maneja errores de validación de Bean Validation en el cuerpo de la petición.
     *
     * @param e excepción con los errores de validación de campos
     * @return respuesta HTTP 400 con el primer mensaje de validación
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> validacion(MethodArgumentNotValidException e) {
        String mensaje = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null
                        ? "Datos inválidos."
                        : error.getDefaultMessage())
                .orElse("Datos inválidos.");
        return ResponseEntity.badRequest().body(mensaje);
    }
}
