package com.portalcreditos.security;

import com.portalcreditos.entity.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Genera los tokens JWT que se entregan al iniciar sesion.
 * El token contiene: usuario (sub), rol, id del cliente y nombre para mostrar.
 * NO contiene la contrasena ni datos financieros.
 */
@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final String emisor;
    private final long expiracionMinutos;

    public JwtService(JwtEncoder encoder,
                      @Value("${app.jwt.emisor}") String emisor,
                      @Value("${app.jwt.expiracion-minutos}") long expiracionMinutos) {
        this.encoder = encoder;
        this.emisor = emisor;
        this.expiracionMinutos = expiracionMinutos;
    }

    public TokenGenerado generar(Usuario usuario, String nombre) {
        Instant ahora = Instant.now();
        Instant expira = ahora.plus(expiracionMinutos, ChronoUnit.MINUTES);
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(emisor)
                .issuedAt(ahora)
                .expiresAt(expira)
                .subject(usuario.getUsername())
                .claim("rol", usuario.getRol().name())
                .claim("nombre", nombre);
        if (usuario.getCliente() != null) {
            claims.claim("clienteId", usuario.getCliente().getId());
        }
        JwsHeader cabecera = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(cabecera, claims.build())).getTokenValue();
        return new TokenGenerado(token, expira);
    }

    public record TokenGenerado(String token, Instant expiraEn) {
    }
}
