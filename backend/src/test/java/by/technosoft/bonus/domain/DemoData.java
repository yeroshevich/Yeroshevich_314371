package by.technosoft.bonus.domain;

import static by.technosoft.bonus.domain.TestEntities.account;
import static by.technosoft.bonus.domain.TestEntities.department;
import static by.technosoft.bonus.domain.TestEntities.employee;
import static by.technosoft.bonus.domain.TestEntities.user;

/**
 * Небольшой набор связанных сущностей по мотивам демо-данных V2 — создаётся заново в каждом тесте,
 * поэтому изменения баланса и статусов не переходят между тестами.
 * <ul>
 *   <li>Подразделение 1 «Разработка ПО»: руководитель Дмитрий Савицкий (сотрудник 2, пользователь 2),
 *       подчинённые Анна Ковалёва (1) и Максим Новик (3);</li>
 *   <li>Подразделение 2 «Обеспечение качества»: руководитель Наталья Кравченко (8), подчинённый Павел Карпович (9) —
 *       его счёт заблокирован;</li>
 *   <li>HR Ольга Мельник (20), финотдел Игорь Жук (23), администратор Сергей Литвинов (17).</li>
 * </ul>
 */
public final class DemoData {

    public final Department development = department(1, "Разработка ПО");
    public final Department qa = department(2, "Обеспечение качества");
    public final Department hrDepartment = department(5, "Управление персоналом");

    public final Employee dmitry = employee(2, "Дмитрий Савицкий", development, null);
    public final Employee anna = employee(1, "Анна Ковалёва", development, dmitry);
    public final Employee maxim = employee(3, "Максим Новик", development, dmitry);
    public final Employee natalia = employee(8, "Наталья Кравченко", qa, null);
    public final Employee pavel = employee(9, "Павел Карпович", qa, natalia);
    public final Employee olga = employee(20, "Ольга Мельник", hrDepartment, null);
    public final Employee igor = employee(23, "Игорь Жук", hrDepartment, null);
    public final Employee sergey = employee(17, "Сергей Литвинов", hrDepartment, null);

    public final AppUser employeeUser = user(1, anna, RoleCode.EMPLOYEE);
    public final AppUser managerUser = user(2, dmitry, RoleCode.MANAGER);
    public final AppUser hrUser = user(20, olga, RoleCode.HR);
    public final AppUser financeUser = user(23, igor, RoleCode.FINANCE);
    public final AppUser adminUser = user(17, sergey, RoleCode.ADMIN);

    public final BonusAccount annaAccount = account(1, anna, 8900);
    public final BonusAccount maximAccount = account(3, maxim, 9950);
    public final BonusAccount pavelAccount = account(9, pavel, 0, AccountStatus.BLOCKED);

    public DemoData() {
        TestEntities.set(development, "head", dmitry);
        TestEntities.set(qa, "head", natalia);
    }
}
