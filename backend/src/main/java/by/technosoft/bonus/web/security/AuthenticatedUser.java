package by.technosoft.bonus.web.security;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.domain.RoleCode;

/**
 * Текущий пользователь запроса (principal в SecurityContext). Контроллеры получают его через
 * {@code @AuthenticationPrincipal} и передают id в сервисы — сервисы не зависят от Spring Security.
 *
 * @param userId     учётная запись
 * @param employeeId сотрудник и его бонусный счёт
 */
public record AuthenticatedUser(Long userId, Long employeeId, RoleCode role, String email) {

    public static AuthenticatedUser of(AppUser user) {
        return new AuthenticatedUser(user.getId(), user.getEmployee().getId(), user.getRoleCode(),
                user.getEmployee().getEmail());
    }

    /** Полномочие Spring Security: {@code ROLE_hr} и т. п. (проверяется в {@code @PreAuthorize}). */
    public String authority() {
        return "ROLE_" + role.dbValue();
    }
}
