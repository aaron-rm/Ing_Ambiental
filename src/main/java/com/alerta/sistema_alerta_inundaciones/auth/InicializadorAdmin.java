package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.Rol;
import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea o promueve la cuenta administradora configurada por entorno. La
 * contraseña nunca se registra en logs ni se guarda en el repositorio.
 */
@Component
public class InicializadorAdmin implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InicializadorAdmin.class);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String identificador;
    private final String password;

    public InicializadorAdmin(UsuarioRepository usuarioRepository,
                              PasswordEncoder passwordEncoder,
                              @Value("${alerta.admin.identificador:admin}") String identificador,
                              @Value("${alerta.admin.password:}") String password) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.identificador = identificador;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (password == null || password.isBlank()) {
            log.warn("ALERTA_ADMIN_PASSWORD no está configurado. No se creará la cuenta administradora.");
            return;
        }
        if (password.length() < 8) {
            log.warn("ALERTA_ADMIN_PASSWORD tiene menos de 8 caracteres. No se creará la cuenta administradora.");
            return;
        }

        String normalizado = Usuario.normalizar(identificador);
        if (normalizado == null || normalizado.isBlank()) {
            log.warn("ALERTA_ADMIN_IDENTIFICADOR está vacío. No se creará la cuenta administradora.");
            return;
        }

        var existente = usuarioRepository.findByIdentificador(normalizado);
        if (existente.isPresent()) {
            Usuario usuario = existente.get();
            if (usuario.getRol() != Rol.ADMIN) {
                usuario.setRol(Rol.ADMIN);
                usuarioRepository.save(usuario);
                log.info("La cuenta '{}' fue promovida a ADMIN.", normalizado);
            }
            return;
        }

        usuarioRepository.save(new Usuario(normalizado, passwordEncoder.encode(password), Rol.ADMIN));
        log.info("Cuenta administradora '{}' creada.", normalizado);
    }
}
