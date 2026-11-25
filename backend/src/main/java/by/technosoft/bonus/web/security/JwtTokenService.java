package by.technosoft.bonus.web.security;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.config.SecurityProperties;
import by.technosoft.bonus.domain.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT HS256: {@code sub} — id учётной записи, {@code role} — роль (для журналов и клиента),
 * {@code iss}, {@code iat}, {@code exp} (срок — {@code bonus.security.jwt.ttl}, 8 ч).
 * Секрет — {@code bonus.security.jwt.secret} (не короче 32 байт, иначе запуск прерывается).
 */
@Component
public class JwtTokenService implements TokenService {

    private final SecretKey key;
    private final SecurityProperties.Jwt settings;
    private final Clock clock;

    public JwtTokenService(SecurityProperties properties, Clock clock) {
        this.settings = properties.jwt();
        this.key = Keys.hmacShaKeyFor(settings.secret().getBytes(StandardCharsets.UTF_8));
        this.clock = clock;
    }

    @Override
    public IssuedToken issue(AppUser user) {
        Instant now = clock.instant();
        Instant expires = now.plus(settings.ttl());
        String token = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(settings.issuer())
                .claim("role", user.getRoleCode().dbValue())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, OffsetDateTime.ofInstant(expires, clock.getZone()));
    }

    @Override
    public Optional<Long> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .requireIssuer(settings.issuer())
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
