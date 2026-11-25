package by.technosoft.bonus.service.auth;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.InvalidCredentialsException;
import by.technosoft.bonus.service.exception.UserBlockedException;

/**
 * Аутентификация (SEC-1): проверка email и пароля (BCrypt, DATA-4), фиксация входа и выхода в аудите.
 * Выпуск токена — забота REST-слоя; сервис работает только с учётными записями.
 */
@Service
public class AuthenticationService {

    private final AppUserRepository userRepository;
    private final AccessPolicy accessPolicy;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrail auditTrail;
    private final Clock clock;
    /**
     * Хеш случайного пароля: сверяется, когда email не найден, чтобы время ответа
     * не выдавало, есть ли такой пользователь.
     */
    private final String dummyHash;

    public AuthenticationService(AppUserRepository userRepository, AccessPolicy accessPolicy,
                                 PasswordEncoder passwordEncoder, AuditTrail auditTrail, Clock clock) {
        this.userRepository = userRepository;
        this.accessPolicy = accessPolicy;
        this.passwordEncoder = passwordEncoder;
        this.auditTrail = auditTrail;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    /**
     * Вход по email и паролю. Неверная пара — {@link InvalidCredentialsException} (одинаково для
     * неизвестного email и неверного пароля); заблокированная учётная запись — {@link UserBlockedException}.
     */
    @Transactional
    public AppUser login(String email, String password) {
        Optional<AppUser> found = userRepository.findByEmployeeEmailIgnoreCase(email == null ? "" : email.trim());
        String hash = found.map(AppUser::getPasswordHash).orElse(dummyHash);
        boolean matches = passwordEncoder.matches(password == null ? "" : password, hash);
        if (found.isEmpty() || !matches) {
            throw new InvalidCredentialsException();
        }
        AppUser user = found.get();
        if (!user.isActive()) {
            throw new UserBlockedException();
        }
        userRepository.touchLastLogin(user.getId(), OffsetDateTime.now(clock));
        auditTrail.record(user.getId(), "Вход в систему", "session", user.getId(),
                Map.of("label", user.getEmployee().getEmail()));
        return accessPolicy.requireActiveUser(user.getId());
    }

    /** Активная учётная запись по id из токена (роль и сотрудник загружены). */
    @Transactional(readOnly = true)
    public AppUser activeUser(Long userId) {
        return accessPolicy.requireActiveUser(userId);
    }

    /** Выход: токен без состояния удаляет клиент, сервер фиксирует событие в аудите. */
    @Transactional
    public void logout(Long userId) {
        AppUser user = accessPolicy.requireActiveUser(userId);
        auditTrail.record(user.getId(), "Выход из системы", "session", user.getId(),
                Map.of("label", user.getEmployee().getEmail()));
    }
}
