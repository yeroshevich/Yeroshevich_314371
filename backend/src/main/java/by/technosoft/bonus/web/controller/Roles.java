package by.technosoft.bonus.web.controller;

/**
 * Выражения {@code @PreAuthorize} для ролевой модели (SEC-3) — те же наборы ролей, что в меню
 * клиента ({@code frontend/src/routes.tsx}). Один источник вместо строк в каждом методе (DRY).
 */
public final class Roles {

    /** Владельцы бонусного счёта с экранами «Мой баланс», «История», «Программы». */
    public static final String ACCOUNT_HOLDER = "hasAnyRole('employee', 'manager', 'hr')";
    public static final String MANAGER_OR_HR = "hasAnyRole('manager', 'hr')";
    public static final String HR = "hasRole('hr')";
    public static final String HR_OR_FINANCE = "hasAnyRole('hr', 'finance')";
    public static final String FINANCE = "hasRole('finance')";
    public static final String ADMIN = "hasRole('admin')";
    /** Справочник сотрудников: руководитель (своя команда), HR, финотдел, администратор. */
    public static final String STAFF_VIEWERS = "hasAnyRole('manager', 'hr', 'finance', 'admin')";

    private Roles() {
    }
}
