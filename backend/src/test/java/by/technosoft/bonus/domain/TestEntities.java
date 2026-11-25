package by.technosoft.bonus.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * Тестовые сущности для UNIT-тестов без БД и Spring-контекста.
 * Доменные сущности неизменяемы снаружи (защищённый конструктор JPA, нет сеттеров), поэтому
 * фабрика находится в том же пакете и заполняет поля через {@link ReflectionTestUtils}.
 * Данные повторяют демо-данные V2 (сотрудники, роли, правила, кампании).
 */
public final class TestEntities {

    /** Часовой пояс компании (bonus.zone). */
    public static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    /** «Сегодня» во всех UNIT-тестах: 25.11.2026 12:00 по Минску (как в демо-данных). */
    public static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, ZONE);

    public static final LocalDate TODAY = NOW.toLocalDate();

    private TestEntities() {
    }

    public static Clock clock() {
        return Clock.fixed(NOW.toInstant(), ZONE);
    }

    public static OffsetDateTime now() {
        return NOW.toOffsetDateTime();
    }

    /** Момент «дата + время» по Минску. */
    public static OffsetDateTime at(String date, int hour) {
        return LocalDate.parse(date).atTime(hour, 0).atZone(ZONE).toOffsetDateTime();
    }

    public static Department department(long id, String name) {
        Department d = new Department();
        set(d, "id", id);
        set(d, "name", name);
        return d;
    }

    public static Department department(long id, String name, Employee head) {
        Department d = department(id, name);
        set(d, "head", head);
        return d;
    }

    public static Employee employee(long id, String fullName, Department department, Employee manager) {
        Employee e = new Employee();
        set(e, "id", id);
        set(e, "fullName", fullName);
        set(e, "email", "e" + id + "@technosoft.by");
        set(e, "position", "Инженер");
        set(e, "department", department);
        set(e, "manager", manager);
        set(e, "hiredAt", LocalDate.of(2020, 1, 1));
        return e;
    }

    public static Role role(RoleCode code) {
        Role r = new Role();
        set(r, "code", code.dbValue());
        set(r, "name", code.name());
        return r;
    }

    public static AppUser user(long id, Employee employee, RoleCode role) {
        return user(id, employee, role, UserStatus.ACTIVE);
    }

    public static AppUser user(long id, Employee employee, RoleCode role, UserStatus status) {
        AppUser u = new AppUser();
        set(u, "id", id);
        set(u, "employee", employee);
        set(u, "role", role(role));
        set(u, "passwordHash", "{noop}demo");
        set(u, "status", status);
        set(u, "createdAt", at("2026-01-01", 9));
        return u;
    }

    public static BonusAccount account(long id, Employee employee, int balance) {
        return account(id, employee, balance, AccountStatus.ACTIVE);
    }

    public static BonusAccount account(long id, Employee employee, int balance, AccountStatus status) {
        BonusAccount a = new BonusAccount();
        set(a, "id", id);
        set(a, "employee", employee);
        set(a, "balance", balance);
        set(a, "status", status);
        set(a, "updatedAt", at("2026-11-01", 9));
        return a;
    }

    public static Kpi kpi(long id, String code, String name) {
        Kpi k = new Kpi();
        set(k, "id", id);
        set(k, "code", code);
        set(k, "name", name);
        set(k, "unit", "%");
        return k;
    }

    /** Правило начисления; params — JSON, например {@code {"roundTo": 50}}. */
    public static AccrualRule rule(long id, String name, Kpi kpi, String weight, int basePoints,
                                   RulePeriodicity periodicity, String threshold, String params,
                                   boolean active, LocalDate effectiveFrom) {
        AccrualRule r = new AccrualRule();
        set(r, "id", id);
        set(r, "name", name);
        set(r, "kpi", kpi);
        set(r, "weight", new BigDecimal(weight));
        set(r, "basePoints", basePoints);
        set(r, "periodicity", periodicity);
        set(r, "threshold", new BigDecimal(threshold));
        set(r, "params", params);
        set(r, "active", active);
        set(r, "effectiveFrom", effectiveFrom);
        set(r, "updatedAt", at("2026-06-01", 9));
        return r;
    }

    /** Правило 1 демо-данных: «Выполнение плана спринта», 3000 × 0,4 × KPI, порог 80 %, округление до 50. */
    public static AccrualRule sprintRule() {
        return rule(1, "Выполнение плана спринта", kpi(1, "SPRINT", "Закрытые задачи спринта"), "0.40", 3000,
                RulePeriodicity.MONTH, "80.00", "{\"roundTo\": 50}", true, LocalDate.of(2026, 6, 1));
    }

    public static Campaign campaign(long id, String name, String start, String end, String coefficient,
                                    Integer budget, CampaignStatus status) {
        Campaign c = new Campaign();
        set(c, "id", id);
        set(c, "name", name);
        set(c, "description", "");
        set(c, "startDate", LocalDate.parse(start));
        set(c, "endDate", LocalDate.parse(end));
        set(c, "coefficient", new BigDecimal(coefficient));
        set(c, "budget", budget);
        set(c, "status", status);
        set(c, "createdAt", at("2026-08-20", 9));
        return c;
    }

    public static Program program(long id, String name, int cost, boolean active, String inactiveReason) {
        Program p = new Program();
        set(p, "id", id);
        set(p, "name", name);
        set(p, "category", ProgramCategory.values()[0]);
        set(p, "description", "");
        set(p, "cost", cost);
        set(p, "active", active);
        set(p, "inactiveReason", inactiveReason);
        return p;
    }

    public static Integration integration(long id, IntegrationKind kind, String name, String url,
                                          String apiKeyEncrypted, boolean enabled) {
        Integration i = new Integration();
        set(i, "id", id);
        set(i, "kind", kind);
        set(i, "name", name);
        set(i, "url", url);
        set(i, "apiKeyEncrypted", apiKeyEncrypted);
        set(i, "enabled", enabled);
        return i;
    }

    public static IntegrationCheck check(long id, Integration integration, boolean ok, String message) {
        IntegrationCheck c = new IntegrationCheck();
        set(c, "id", id);
        set(c, "integration", integration);
        set(c, "ok", ok);
        set(c, "message", message);
        set(c, "latencyMs", ok ? 90 : null);
        set(c, "checkedAt", now());
        return c;
    }

    /** Операция, уже сохранённая в журнале (как после загрузки из БД). */
    public static Operation operation(long id, BonusAccount account, OperationType type, OperationStatus status,
                                      OperationBasis basis, int amount, OffsetDateTime createdAt) {
        Operation op = Operation.create(new OperationNumber(id, String.format("ОП-%06d", id)), account, type, status,
                basis, amount, "Тестовая операция " + id, null, createdAt);
        op.markNotNew();
        return op;
    }

    public static BusinessEvent event(long id, Integration source, String type, Employee employee, String payload,
                                      OffsetDateTime receivedAt) {
        BusinessEvent e = BusinessEvent.received(source, "EXT-" + id, type, employee, payload, receivedAt);
        set(e, "id", id);
        return e;
    }

    /** Установка поля сущности (в том числе статуса — имитация данных БД). */
    public static void set(Object target, String field, Object value) {
        ReflectionTestUtils.setField(target, field, value);
    }
}
