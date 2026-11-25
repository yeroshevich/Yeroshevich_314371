package by.technosoft.bonus.service.admin;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.config.SecurityProperties;
import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.Department;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.domain.UserStatus;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.repository.BonusAccountRepository;
import by.technosoft.bonus.repository.DepartmentRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.service.AccessPolicy;
import by.technosoft.bonus.service.audit.AuditTrail;
import by.technosoft.bonus.service.exception.ConflictException;
import by.technosoft.bonus.service.exception.DomainValidationException;
import by.technosoft.bonus.service.exception.DuplicateEmailException;
import by.technosoft.bonus.service.query.UserQueryService;

/**
 * Управление учётными записями администратором (UC12): создание (сотрудник + бонусный счёт +
 * учётная запись с временным паролем), изменение данных и роли, блокировка и разблокировка.
 * Email уникален (альт. поток UC12 — {@link DuplicateEmailException}).
 */
@Service
public class UserAdminService {

    /**
     * Данные пользователя.
     *
     * @param departmentId подразделение; руководителем нового сотрудника становится его глава
     */
    public record UserCommand(String fullName, String email, Long departmentId, String position, RoleCode role) {
    }

    private final AccessPolicy accessPolicy;
    private final AppUserRepository userRepository;
    private final EmployeeRepository employeeRepository;
    private final DepartmentRepository departmentRepository;
    private final BonusAccountRepository accountRepository;
    private final UserQueryService userQueryService;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final AuditTrail auditTrail;
    private final Clock clock;

    public UserAdminService(AccessPolicy accessPolicy,
                            AppUserRepository userRepository,
                            EmployeeRepository employeeRepository,
                            DepartmentRepository departmentRepository,
                            BonusAccountRepository accountRepository,
                            UserQueryService userQueryService,
                            PasswordEncoder passwordEncoder,
                            SecurityProperties securityProperties,
                            AuditTrail auditTrail,
                            Clock clock) {
        this.accessPolicy = accessPolicy;
        this.userRepository = userRepository;
        this.employeeRepository = employeeRepository;
        this.departmentRepository = departmentRepository;
        this.accountRepository = accountRepository;
        this.userQueryService = userQueryService;
        this.passwordEncoder = passwordEncoder;
        this.securityProperties = securityProperties;
        this.auditTrail = auditTrail;
        this.clock = clock;
    }

    /** Новый сотрудник с пустым бонусным счётом и учётной записью (временный пароль из настроек). */
    @Transactional
    public AppUser create(Long actorUserId, UserCommand command) {
        requireAdmin(actorUserId);
        String email = normalizeEmail(command.email());
        if (employeeRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateEmailException(email);
        }
        Department department = requireDepartment(command.departmentId());
        OffsetDateTime now = OffsetDateTime.now(clock);

        long employeeId = employeeRepository.nextId();
        employeeRepository.insert(employeeId, command.fullName().trim(), email, command.position().trim(),
                department.getId(), headOf(department, null), LocalDate.now(clock));
        accountRepository.open(employeeId, now);
        long userId = userRepository.nextId();
        userRepository.insert(userId, employeeId, command.role().dbValue(),
                passwordEncoder.encode(securityProperties.initialPassword()), now);

        auditTrail.record(actorUserId, "Создание пользователя", "app_user", userId,
                Map.of("label", command.fullName().trim(), "email", email, "role", command.role().dbValue()));
        return userQueryService.get(userId);
    }

    /** Изменение ФИО, email, подразделения, должности и роли. */
    @Transactional
    public AppUser update(Long actorUserId, Long userId, UserCommand command) {
        requireAdmin(actorUserId);
        AppUser user = userQueryService.get(userId);
        Employee employee = user.getEmployee();
        String email = normalizeEmail(command.email());
        if (employeeRepository.existsByEmailIgnoreCaseAndIdNot(email, employee.getId())) {
            throw new DuplicateEmailException(email);
        }
        if (user.getId().equals(actorUserId) && command.role() != RoleCode.ADMIN) {
            throw new ConflictException("Нельзя снять роль администратора с собственной учётной записи.");
        }
        Department department = requireDepartment(command.departmentId());
        Long managerId = department.getId().equals(employee.getDepartment().getId())
                ? (employee.getManager() == null ? null : employee.getManager().getId())
                : headOf(department, employee.getId());
        boolean roleChanged = user.getRoleCode() != command.role();

        employeeRepository.update(employee.getId(), command.fullName().trim(), email, command.position().trim(),
                department.getId(), managerId);
        if (roleChanged) {
            userRepository.updateRole(userId, command.role().dbValue());
        }
        auditTrail.record(actorUserId, roleChanged ? "Изменение роли" : "Изменение пользователя", "app_user", userId,
                Map.of("label", command.fullName().trim() + (roleChanged ? " → " + command.role().dbValue() : "")));
        return userQueryService.get(userId);
    }

    /** Блокировка или разблокировка входа; повторная установка того же статуса ничего не меняет. */
    @Transactional
    public AppUser setStatus(Long actorUserId, Long userId, UserStatus status) {
        requireAdmin(actorUserId);
        if (userId.equals(actorUserId)) {
            throw new ConflictException("Нельзя заблокировать собственную учётную запись.");
        }
        AppUser user = userQueryService.get(userId);
        if (user.getStatus() != status) {
            userRepository.updateStatus(userId, status.dbValue());
            auditTrail.record(actorUserId,
                    status == UserStatus.BLOCKED ? "Блокировка пользователя" : "Разблокировка пользователя",
                    "app_user", userId, Map.of("label", user.getEmployee().getFullName()));
        }
        return userQueryService.get(userId);
    }

    private void requireAdmin(Long actorUserId) {
        AppUser actor = accessPolicy.requireActiveUser(actorUserId);
        accessPolicy.requireRole(actor, RoleCode.ADMIN);
    }

    private Department requireDepartment(Long departmentId) {
        if (departmentId == null) {
            throw new DomainValidationException("Выберите подразделение.");
        }
        return departmentRepository.findById(departmentId)
                .orElseThrow(() -> new DomainValidationException("Подразделение не найдено. Обновите страницу."));
    }

    /** Руководитель сотрудника — глава подразделения (сам глава руководителя не имеет). */
    private static Long headOf(Department department, Long employeeId) {
        Employee head = department.getHead();
        return head == null || head.getId().equals(employeeId) ? null : head.getId();
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
