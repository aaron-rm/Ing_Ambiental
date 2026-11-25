package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.UsuarioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de seguridad HTTP de Spring Security: define la política sin estado (JWT),
 * las reglas de autorización por ruta y rol, el codificador de contraseñas y CORS.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    /**
     * Crea la configuración de seguridad preparando el filtro de autenticación JWT.
     *
     * @param jwtService        servicio de tokens JWT
     * @param usuarioRepository repositorio de usuarios
     */
    public SecurityConfig(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtFilter = new JwtAuthenticationFilter(jwtService, usuarioRepository);
    }

    /**
     * Expone el codificador de contraseñas basado en BCrypt.
     *
     * @return instancia de {@link BCryptPasswordEncoder}
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Configura la cadena de filtros de seguridad, rutas públicas, rutas autenticadas,
     * permisos por rol y manejo de errores 401/403 en JSON.
     *
     * @param http configurador de seguridad HTTP de Spring
     * @return cadena de filtros de seguridad construida
     * @throws Exception si falla la configuración de seguridad
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, error) ->
                                escribirError(response, 401, "No autenticado o token inválido."))
                        .accessDeniedHandler((request, response, error) ->
                                escribirError(response, 403, "No tienes permiso para esta operación.")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/app.js", "/styles.css",
                                "/manifest.json", "/firebase-messaging-sw.js", "/icons/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/rios/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/rios/*/muestras", "/api/rios/*/muestras/csv")
                                .hasAnyRole("ADMIN", "INVESTIGADOR")
                        .requestMatchers(HttpMethod.PATCH, "/api/rios/*").hasAnyRole("ADMIN", "INVESTIGADOR")
                        .requestMatchers(HttpMethod.POST, "/api/rios").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/alertas/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/alertas/suscribir").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/alertas/simular").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/alertas/evaluar/**").hasRole("ADMIN")
                        .requestMatchers("/api/alertas/suscripciones/**").permitAll()
                        .requestMatchers("/api/push/config").permitAll()
                        .requestMatchers("/api/push/demo").permitAll()
                        .requestMatchers("/api/push/**").authenticated()
                        .requestMatchers("/api/usuarios/**").authenticated()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * Define la configuración CORS permitiendo los orígenes configurados en propiedades.
     *
     * @param origenes lista de orígenes o patrones separados por comas
     * @return fuente de configuración CORS para todas las rutas
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${alerta.cors.origenes:http://localhost:3000,http://localhost:5173,http://localhost:8081,http://127.0.0.1:8081}") String origenes) {
        CorsConfiguration config = new CorsConfiguration();
        // Se aceptan origenes exactos y, opcionalmente, patrones como
        // https://*.trycloudflare.com para la demo con tuneles temporales.
        config.setAllowedOriginPatterns(Arrays.stream(origenes.split(","))
                .map(String::trim)
                .filter(origen -> !origen.isBlank())
                .toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Admin-Token"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // Escribe una respuesta JSON simple con código de estado HTTP y clave "error".
    private static void escribirError(jakarta.servlet.http.HttpServletResponse response,
                                      int estado, String mensaje) throws java.io.IOException {
        response.setStatus(estado);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }
}
