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

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtFilter;

    public SecurityConfig(JwtService jwtService, UsuarioRepository usuarioRepository) {
        this.jwtFilter = new JwtAuthenticationFilter(jwtService, usuarioRepository);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

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
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Admin-Token"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void escribirError(jakarta.servlet.http.HttpServletResponse response,
                                      int estado, String mensaje) throws java.io.IOException {
        response.setStatus(estado);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }
}
