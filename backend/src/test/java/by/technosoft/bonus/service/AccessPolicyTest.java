package by.technosoft.bonus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.domain.UserStatus;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;

/** Ролевая модель (SEC-3) на уровне бизнес-логики: кто действует и над чьими данными. */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccessPolicy — права доступа (SEC-3)")
class AccessPolicyTest {

    @Mock private AppUserRepository userRepository;
    @InjectMocks private AccessPolicy policy;

    private final DemoData d = new DemoData();

    @Test
    @DisplayName("FR5-1: активный пользователь загружается вместе с ролью")
    void activeUserIsLoaded() {
        when(userRepository.findWithRoleById(2L)).thenReturn(Optional.of(d.managerUser));
        assertThat(policy.requireActiveUser(2L)).isSameAs(d.managerUser);
    }

    @Test
    @DisplayName("FR12-3: деактивированная учётная запись не может выполнять действия")
    void blockedUserIsDenied() {
        AppUser blocked = TestEntities.user(26, d.anna, RoleCode.EMPLOYEE, UserStatus.BLOCKED);
        when(userRepository.findWithRoleById(26L)).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> policy.requireActiveUser(26L))
                .isInstanceOf(AccessDeniedDomainException.class)
                .hasMessage("Учётная запись заблокирована. Обратитесь к администратору системы.");
    }

    @Test
    @DisplayName("FR5-1: без сессии или с неизвестной учётной записью — отказ «Сессия закончилась»")
    void missingSessionIsDenied() {
        assertThatThrownBy(() -> policy.requireActiveUser(null)).hasMessage("Сессия закончилась. Войдите снова.");
        verifyNoInteractions(userRepository);
        when(userRepository.findWithRoleById(404L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> policy.requireActiveUser(404L)).isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    @DisplayName("FR12-2: действие разрешено только ролям из списка")
    void roleIsChecked() {
        assertThatCode(() -> policy.requireRole(d.hrUser, RoleCode.MANAGER, RoleCode.HR)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireRole(d.employeeUser, RoleCode.MANAGER, RoleCode.HR))
                .isInstanceOf(AccessDeniedDomainException.class)
                .hasMessage("Для этого действия у вашей роли нет прав.");
        assertThatThrownBy(() -> policy.requireRole(d.adminUser)).isInstanceOf(AccessDeniedDomainException.class);
    }

    @Test
    @DisplayName("FR5-1: HR управляет баллами любого сотрудника, руководитель — только непосредственных подчинённых")
    void manageScope() {
        assertThatCode(() -> policy.requireCanManage(d.hrUser, d.pavel)).doesNotThrowAnyException();
        assertThatCode(() -> policy.requireCanManage(d.managerUser, d.anna)).doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.requireCanManage(d.managerUser, d.pavel))
                .isInstanceOf(AccessDeniedDomainException.class)
                .hasMessage("Руководитель может управлять баллами только своей команды.");
        assertThatThrownBy(() -> policy.requireCanManage(d.managerUser, d.dmitry))
                .isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> policy.requireCanManage(d.financeUser, d.anna))
                .isInstanceOf(AccessDeniedDomainException.class);
    }
}
