package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Filtro HTTP que extrae el token Bearer del encabezado {@code Authorization},
 * lo valida con {@link JwtService} y carga el {@link Usuario} autenticado en el
 * contexto de seguridad de Spring en cada petición.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final JwtService jwtService;
    private final UsuarioRepository usuarioRepository;

    /**
     * Crea el filtro de autenticación JWT.
     *
     * @param jwtService        servicio de validación y lectura de tokens JWT
     * @param usuarioRepository repositorio para recuperar el usuario propietario del token
     */
    public JwtAuthenticationFilter(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtService = jwtService;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Procesa la petición HTTP buscando un token JWT válido y poblando el contexto de seguridad.
     *
     * @param request     petición HTTP entrante
     * @param response    respuesta HTTP saliente
     * @param filterChain cadena de filtros a continuar
     * @throws ServletException si ocurre un error en el procesamiento del servlet
     * @throws IOException      si ocurre un error de entrada/salida
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String encabezado = request.getHeader("Authorization");
        if (encabezado != null && encabezado.startsWith(PREFIJO)) {
            String token = encabezado.substring(PREFIJO.length()).trim();
            try {
                String identificador = jwtService.identificadorDe(token);
                usuarioRepository.findByIdentificador(identificador)
                        .ifPresent(usuario -> autenticar(usuario, request));
            } catch (JwtException | IllegalArgumentException e) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }

    // Registra al usuario y su rol como autoridad ROLE_<ROL> en el SecurityContextHolder.
    private void autenticar(Usuario usuario, HttpServletRequest request) {
        var autoridades = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getRol().name()));
        var autenticacion = new UsernamePasswordAuthenticationToken(usuario, null, autoridades);
        autenticacion.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }
}
