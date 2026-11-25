package by.technosoft.bonus.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Параметры безопасности (префикс {@code bonus.security} в application.yml).
 *
 * @param jwt             подпись и срок действия токенов доступа (SEC-1)
 * @param secretKey       ключ AES-256 (base64) для шифрования API-ключей интеграций (DATA-4)
 * @param initialPassword временный пароль новой учётной записи (UC12)
 * @param corsOrigins     адреса клиентской части, которым разрешены запросы из браузера
 */
@ConfigurationProperties(prefix = "bonus.security")
public record SecurityProperties(
        Jwt jwt,
        String secretKey,
        @DefaultValue("demo") String initialPassword,
        @DefaultValue("http://localhost:5173") List<String> corsOrigins) {

    /**
     * @param secret секрет HS256, не короче 32 байт
     * @param ttl    срок действия токена
     * @param issuer издатель (claim iss)
     */
    public record Jwt(String secret, @DefaultValue("PT8H") Duration ttl, @DefaultValue("technosoft-bonus") String issuer) {
    }
}
