package by.technosoft.bonus.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

import by.technosoft.bonus.config.SecurityProperties;
import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.RoleCode;

class JwtTokenServiceTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-32b";
    private static final Instant NOW = Instant.parse("2026-11-25T08:00:00Z");
    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    private static JwtTokenService service(String secret, String issuer, Instant now) {
        SecurityProperties props = new SecurityProperties(
                new SecurityProperties.Jwt(secret, Duration.ofHours(8), issuer), null, "demo", List.of());
        return new JwtTokenService(props, Clock.fixed(now, ZONE));
    }

    private static AppUser user(long id) {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(id);
        when(user.getRoleCode()).thenReturn(RoleCode.HR);
        return user;
    }

    @Test
    void issuedTokenIsVerifiedUntilExpiry() {
        TokenService.IssuedToken token = service(SECRET, "tsb", NOW).issue(user(20));
        assertThat(token.expiresAt().toInstant()).isEqualTo(NOW.plus(Duration.ofHours(8)));
        assertThat(service(SECRET, "tsb", NOW.plus(Duration.ofHours(7))).verify(token.value())).contains(20L);
        assertThat(service(SECRET, "tsb", NOW.plus(Duration.ofHours(9))).verify(token.value())).isEmpty();
    }

    @Test
    void foreignSignatureIssuerOrGarbageIsRejected() {
        String token = service(SECRET, "tsb", NOW).issue(user(20)).value();
        assertThat(service("another-secret-another-secret-another-32", "tsb", NOW).verify(token)).isEmpty();
        assertThat(service(SECRET, "other", NOW).verify(token)).isEmpty();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(service(SECRET, "tsb", NOW).verify(tampered)).isEmpty();
        assertThat(service(SECRET, "tsb", NOW).verify("not-a-jwt")).isEmpty();
    }
}
