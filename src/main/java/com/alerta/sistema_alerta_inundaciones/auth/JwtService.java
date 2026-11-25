package com.alerta.sistema_alerta_inundaciones.auth;

import com.alerta.sistema_alerta_inundaciones.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey clave;
    private final Duration expiracion;

    public JwtService(@Value("${alerta.jwt.secret:}") String secreto,
                      @Value("${alerta.jwt.expiracion-horas:24}") long horas) {
        String valor = secreto == null ? "" : secreto.trim();
        if (valor.length() < 32) {
            valor = UUID.randomUUID().toString() + UUID.randomUUID();
            log.warn("ALERTA_JWT_SECRET no está configurado o es corto. Se usarán tokens "
                    + "válidos solo hasta reiniciar el backend. Configure un secreto de al menos 32 caracteres.");
        }
        this.clave = Keys.hmacShaKeyFor(valor.getBytes(StandardCharsets.UTF_8));
        long horasValidas = Math.max(1, horas);
        this.expiracion = Duration.ofHours(horasValidas);
    }

    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        var constructor = Jwts.builder()
                .subject(usuario.getIdentificador())
                .claim("rol", usuario.getRol().name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(expiracion)));
        if (usuario.getId() != null) {
            constructor.claim("usuarioId", usuario.getId());
        }
        return constructor.signWith(clave).compact();
    }

    /** Devuelve el identificador del sujeto o lanza JwtException si el token no es válido. */
    public String identificadorDe(String token) throws JwtException {
        Claims claims = Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
