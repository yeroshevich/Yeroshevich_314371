package by.technosoft.bonus.web.security;

import java.time.OffsetDateTime;
import java.util.Optional;

import by.technosoft.bonus.domain.AppUser;

/**
 * Выпуск и проверка токенов доступа (SEC-1). Фильтр и контроллер входа зависят от интерфейса,
 * а не от библиотеки JWT (DIP); контракт ограничен двумя операциями (ISP).
 */
public interface TokenService {

    /** Токен для пользователя после успешного входа. */
    IssuedToken issue(AppUser user);

    /** Идентификатор пользователя из действительного токена; пусто — подпись неверна или срок истёк. */
    Optional<Long> verify(String token);

    /**
     * @param value     компактная строка токена
     * @param expiresAt окончание срока действия
     */
    record IssuedToken(String value, OffsetDateTime expiresAt) {
    }
}
