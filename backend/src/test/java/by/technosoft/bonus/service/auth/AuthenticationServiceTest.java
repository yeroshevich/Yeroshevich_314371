package by.technosoft.bonus.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.domain.UserStatus;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.InvalidCredentialsException;
import by.technosoft.bonus.service.exception.UserBlockedException;

/** Вход в систему (SEC-1): проверка пароля BCrypt, блокировка, запись входа и выхода в аудит. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthenticationService — вход и выход (SEC-1)")
class AuthenticationServiceTest {

    private static final BCryptPasswordEncoder BCRYPT = new BCryptPasswordEncoder(4);
    private static final String DEMO_HASH = BCRYPT.encode("demo");

    @Mock private AppUserRepository userRepository;
    @Mock private AuditTrail auditTrail;

    private final DemoData d = new DemoData();
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(userRepository, new AccessPolicy(userRepository), BCRYPT, auditTrail,
                TestEntities.clock());
        TestEntities.set(d.employeeUser, "passwordHash", DEMO_HASH);
    }

    @Test
    @DisplayName("SEC-1: пользователь авторизуется по email и паролю; вход фиксируется в аудите")
    void loginSucceeds() {
        when(userRepository.findByEmployeeEmailIgnoreCase("anna.kovaleva@technosoft.by"))
                .thenReturn(Optional.of(d.employeeUser));
        when(userRepository.findWithRoleById(1L)).thenReturn(Optional.of(d.employeeUser));

        AppUser user = service.login("  anna.kovaleva@technosoft.by ", "demo");

        assertThat(user).isSameAs(d.employeeUser);
        verify(userRepository).touchLastLogin(1L, TestEntities.now());
        verify(auditTrail).record(eq(1L), eq("Вход в систему"), eq("session"), eq(1L), any(Map.class));
    }

    @Test
    @DisplayName("SEC-1: неверный пароль или неизвестный email — одинаковый отказ «Неверный email или пароль»")
    void wrongCredentialsAreRejected() {
        when(userRepository.findByEmployeeEmailIgnoreCase("anna.kovaleva@technosoft.by"))
                .thenReturn(Optional.of(d.employeeUser));
        when(userRepository.findByEmployeeEmailIgnoreCase("nobody@technosoft.by")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("anna.kovaleva@technosoft.by", "wrong"))
                .isInstanceOf(InvalidCredentialsException.class);
        assertThatThrownBy(() -> service.login("nobody@technosoft.by", "demo"))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageStartingWith("Неверный email или пароль");
        verify(userRepository, never()).touchLastLogin(anyLong(), any());
    }

    @Test
    @DisplayName("FR12-3: деактивированный пользователь не может войти в систему")
    void blockedUserCannotLogin() {
        AppUser blocked = TestEntities.user(26, d.anna, RoleCode.EMPLOYEE, UserStatus.BLOCKED);
        TestEntities.set(blocked, "passwordHash", DEMO_HASH);
        when(userRepository.findByEmployeeEmailIgnoreCase("gleb.sorokin@technosoft.by")).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> service.login("gleb.sorokin@technosoft.by", "demo"))
                .isInstanceOf(UserBlockedException.class)
                .hasMessage("Учётная запись заблокирована. Обратитесь к администратору системы.");
        verify(auditTrail, never()).record(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("SEC-1: выход из системы фиксируется в аудите")
    void logoutIsAudited() {
        when(userRepository.findWithRoleById(1L)).thenReturn(Optional.of(d.employeeUser));

        service.logout(1L);

        verify(auditTrail).record(eq(1L), eq("Выход из системы"), eq("session"), eq(1L), any(Map.class));
        assertThat(service.activeUser(1L)).isSameAs(d.employeeUser);
    }
}
