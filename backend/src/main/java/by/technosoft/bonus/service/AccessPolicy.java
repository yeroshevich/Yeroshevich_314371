package by.technosoft.bonus.service;

import java.util.Arrays;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.Employee;
import by.technosoft.bonus.domain.RoleCode;
import by.technosoft.bonus.repository.AppUserRepository;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;

/**
 * Проверки ролевой модели (SEC-3) на уровне бизнес-логики:
 * кто выполняет действие и над чьими данными. REST-слой передаёт id пользователя из сессии.
 */
@Component
public class AccessPolicy {

    private final AppUserRepository userRepository;

    public AccessPolicy(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Загружает пользователя-инициатора; неизвестная или заблокированная учётная запись — отказ. */
    public AppUser requireActiveUser(Long userId) {
        if (userId == null) {
            throw new AccessDeniedDomainException("Сессия закончилась. Войдите снова.");
        }
        AppUser user = userRepository.findWithRoleById(userId)
                .orElseThrow(() -> new AccessDeniedDomainException("Сессия закончилась. Войдите снова."));
        if (!user.isActive()) {
            throw new AccessDeniedDomainException("Учётная запись заблокирована. Обратитесь к администратору системы.");
        }
        return user;
    }

    /** Роль пользователя входит в {@code allowed}. */
    public void requireRole(AppUser user, RoleCode... allowed) {
        RoleCode role = user.getRoleCode();
        if (Arrays.stream(allowed).noneMatch(r -> r == role)) {
            throw new AccessDeniedDomainException("Для этого действия у вашей роли нет прав.");
        }
    }

    /**
     * Может ли пользователь управлять баллами сотрудника: HR — любого,
     * руководитель — только своей команды (непосредственные подчинённые).
     */
    public void requireCanManage(AppUser actor, Employee employee) {
        RoleCode role = actor.getRoleCode();
        if (role == RoleCode.HR) {
            return;
        }
        if (role == RoleCode.MANAGER && employee.isManagedBy(actor.getEmployee().getId())) {
            return;
        }
        throw new AccessDeniedDomainException("Руководитель может управлять баллами только своей команды.");
    }
}
