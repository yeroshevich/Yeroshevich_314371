package by.technosoft.bonus.service.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import by.technosoft.bonus.config.SecurityProperties;
import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.DemoData;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.domain.UserStatus;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.DepartmentRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.admin.UserAdminService.UserCommand;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import by.technosoft.bonus.service.exception.ConflictException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.DuplicateEmailException;
import by.technosoft.bonus.service.query.UserQueryService;

/** UC12 «Управление пользователями»: создание, изменение ролей, деактивация. */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserAdminService — управление пользователями (UC12)")
class UserAdminServiceTest {

    private static final long ADMIN = 17L;

    @Mock private AppUserRepository userRepository;
    @Mock private EmployeeRepository employeeRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private BonusAccountRepository accountRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuditTrail auditTrail;

    private final DemoData d = new DemoData();
    private final AppUser maximUser = TestEntities.user(3, d.maxim, RoleCode.EMPLOYEE);
    private UserAdminService service;

    @BeforeEach
    void setUp() {
        SecurityProperties security = new SecurityProperties(null, null, "demo", List.of());
        service = new UserAdminService(new AccessPolicy(userRepository), userRepository, employeeRepository,
                departmentRepository, accountRepository, new UserQueryService(userRepository), passwordEncoder,
                security, auditTrail, TestEntities.clock());
        for (AppUser u : new AppUser[] {d.adminUser, d.hrUser, maximUser}) {
            lenient().when(userRepository.findWithRoleById(u.getId())).thenReturn(Optional.of(u));
        }
        lenient().when(departmentRepository.findById(1L)).thenReturn(Optional.of(d.development));
        lenient().when(departmentRepository.findById(2L)).thenReturn(Optional.of(d.qa));
    }

    private static UserCommand marina(RoleCode role) {
        return new UserCommand(" Марина Захарова ", " Marina.Zakharova@TechnoSoft.by ", 2L, " QA-инженер ", role);
    }

    // ── FR12-1 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR12-1: создаётся сотрудник, его бонусный счёт и учётная запись с временным паролем")
    void createsUser() {
        AppUser created = TestEntities.user(27, TestEntities.employee(27, "Марина Захарова", d.qa, d.natalia),
                RoleCode.EMPLOYEE);
        when(employeeRepository.nextId()).thenReturn(27L);
        when(userRepository.nextId()).thenReturn(27L);
        when(passwordEncoder.encode("demo")).thenReturn("$2a$10$hash");
        when(userRepository.findWithRoleById(27L)).thenReturn(Optional.of(created));

        AppUser result = service.create(ADMIN, marina(RoleCode.EMPLOYEE));

        verify(employeeRepository).insert(27L, "Марина Захарова", "marina.zakharova@technosoft.by", "QA-инженер", 2L,
                8L, TestEntities.TODAY);
        verify(accountRepository).open(27L, TestEntities.now());
        verify(userRepository).insert(27L, 27L, "employee", "$2a$10$hash", TestEntities.now());
        verify(auditTrail).record(ADMIN, "Создание пользователя", "app_user", 27L,
                Map.of("label", "Марина Захарова", "email", "marina.zakharova@technosoft.by", "role", "employee"));
        assertThat(result).isSameAs(created);
    }

    @Test
    @DisplayName("FR12-1: пользователь с уже существующим email не создаётся (EMAIL_EXISTS)")
    void duplicateEmailIsRejected() {
        when(employeeRepository.existsByEmailIgnoreCase("marina.zakharova@technosoft.by")).thenReturn(true);

        assertThatThrownBy(() -> service.create(ADMIN, marina(RoleCode.EMPLOYEE)))
                .isInstanceOfSatisfying(DuplicateEmailException.class,
                        e -> assertThat(e.getCode()).isEqualTo("EMAIL_EXISTS"))
                .hasMessage("Пользователь с email marina.zakharova@technosoft.by уже есть.");
        verify(employeeRepository, never()).nextId();
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("FR12-1: без подразделения или с неизвестным подразделением пользователь не создаётся")
    void departmentIsRequired() {
        UserCommand noDepartment = new UserCommand("Имя", "a@technosoft.by", null, "Инженер", RoleCode.EMPLOYEE);
        UserCommand unknown = new UserCommand("Имя", "b@technosoft.by", 77L, "Инженер", RoleCode.EMPLOYEE);
        when(departmentRepository.findById(77L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(ADMIN, noDepartment)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> service.create(ADMIN, unknown)).isInstanceOf(DomainValidationException.class);
        verify(employeeRepository, never()).insert(anyLong(), anyString(), anyString(), anyString(), anyLong(), any(),
                any());
    }

    @Test
    @DisplayName("FR12-1: управлять пользователями может только администратор")
    void onlyAdminManagesUsers() {
        assertThatThrownBy(() -> service.create(20L, marina(RoleCode.EMPLOYEE)))
                .isInstanceOf(AccessDeniedDomainException.class);
        assertThatThrownBy(() -> service.setStatus(20L, 3L, UserStatus.BLOCKED))
                .isInstanceOf(AccessDeniedDomainException.class);
        verify(userRepository, never()).updateStatus(anyLong(), anyString());
    }

    // ── FR12-2 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR12-2: смена роли сохраняется и фиксируется в аудите как «Изменение роли»")
    void roleIsUpdated() {
        UserCommand promote = new UserCommand("Максим Новик", "maxim.novik@technosoft.by", 1L, "Тимлид",
                RoleCode.MANAGER);

        service.update(ADMIN, 3L, promote);

        verify(userRepository).updateRole(3L, "manager");
        verify(employeeRepository).update(3L, "Максим Новик", "maxim.novik@technosoft.by", "Тимлид", 1L, 2L);
        verify(auditTrail).record(ADMIN, "Изменение роли", "app_user", 3L, Map.of("label", "Максим Новик → manager"));
    }

    @Test
    @DisplayName("FR12-2: без смены роли меняются только данные сотрудника; при переводе руководитель — глава нового отдела")
    void dataUpdatedWithoutRoleChange() {
        UserCommand transfer = new UserCommand("Максим Новик", "maxim.novik@technosoft.by", 2L, "QA-инженер",
                RoleCode.EMPLOYEE);

        service.update(ADMIN, 3L, transfer);

        verify(userRepository, never()).updateRole(anyLong(), anyString());
        verify(employeeRepository).update(3L, "Максим Новик", "maxim.novik@technosoft.by", "QA-инженер", 2L, 8L);
        verify(auditTrail).record(eq(ADMIN), eq("Изменение пользователя"), eq("app_user"), eq(3L), any());
    }

    @Test
    @DisplayName("FR12-2: нельзя снять роль администратора с себя и занять чужой email")
    void updateConflicts() {
        Employee sergey = d.adminUser.getEmployee();
        UserCommand demote = new UserCommand(sergey.getFullName(), sergey.getEmail(), 1L, "Инженер", RoleCode.HR);
        assertThatThrownBy(() -> service.update(ADMIN, ADMIN, demote))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("роль администратора");

        when(employeeRepository.existsByEmailIgnoreCaseAndIdNot("anna.kovaleva@technosoft.by", 3L)).thenReturn(true);
        UserCommand stealEmail = new UserCommand("Максим Новик", "Anna.Kovaleva@technosoft.by", 1L, "Инженер",
                RoleCode.EMPLOYEE);
        assertThatThrownBy(() -> service.update(ADMIN, 3L, stealEmail)).isInstanceOf(DuplicateEmailException.class);
        verify(userRepository, never()).updateRole(anyLong(), anyString());
    }

    // ── FR12-3 ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("FR12-3: администратор деактивирует (блокирует) пользователя; действие фиксируется в аудите")
    void deactivatesUser() {
        service.setStatus(ADMIN, 3L, UserStatus.BLOCKED);

        verify(userRepository).updateStatus(3L, "blocked");
        verify(auditTrail).record(ADMIN, "Блокировка пользователя", "app_user", 3L, Map.of("label", "Максим Новик"));
    }

    @Test
    @DisplayName("FR12-3: повторная установка того же статуса ничего не меняет; разблокировка возвращает доступ")
    void statusChangeIsIdempotent() {
        service.setStatus(ADMIN, 3L, UserStatus.ACTIVE);
        verify(userRepository, never()).updateStatus(anyLong(), anyString());

        AppUser blocked = TestEntities.user(26, d.anna, RoleCode.EMPLOYEE, UserStatus.BLOCKED);
        when(userRepository.findWithRoleById(26L)).thenReturn(Optional.of(blocked));
        service.setStatus(ADMIN, 26L, UserStatus.ACTIVE);
        verify(userRepository).updateStatus(26L, "active");
        verify(auditTrail).record(eq(ADMIN), eq("Разблокировка пользователя"), eq("app_user"), eq(26L), any());
    }

    @Test
    @DisplayName("FR12-3: администратор не может деактивировать собственную учётную запись")
    void cannotBlockSelf() {
        assertThatThrownBy(() -> service.setStatus(ADMIN, ADMIN, UserStatus.BLOCKED))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Нельзя заблокировать собственную учётную запись.");
        verify(userRepository, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    @DisplayName("FR12-3: деактивированный пользователь не может выполнять действия в системе")
    void blockedUserIsDenied() {
        AppUser blocked = TestEntities.user(26, d.anna, RoleCode.ADMIN, UserStatus.BLOCKED);
        when(userRepository.findWithRoleById(26L)).thenReturn(Optional.of(blocked));

        assertThatThrownBy(() -> service.setStatus(26L, 3L, UserStatus.BLOCKED))
                .isInstanceOf(AccessDeniedDomainException.class)
                .hasMessage("Учётная запись заблокирована. Обратитесь к администратору системы.");
    }
}
