package by.technosoft.bonus.web.error;

import org.springframework.http.HttpStatus;

/**
 * Перечень кодов ошибок REST API (поле {@code code} ответа об ошибке) с HTTP-статусом.
 * Доменные коды совпадают с {@code BonusDomainException.getCode()}; технические задаёт REST-слой.
 * Единый источник для обработчика ошибок, фильтров безопасности и документации API.
 */
public enum ErrorCode {

    // ── 400: запрос не разобран ─────────────────────────────────────────────
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "Тело запроса не разобрано или параметр имеет неверный тип/формат"),

    // ── 401: нет аутентификации ─────────────────────────────────────────────
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Нет токена, токен неверен или срок его действия истёк"),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Неверный email или пароль"),

    // ── 403: нет прав ───────────────────────────────────────────────────────
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Роль или область ответственности не позволяет выполнить действие (SEC-3)"),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "Учётная запись заблокирована администратором"),

    // ── 404 / 405 ───────────────────────────────────────────────────────────
    NOT_FOUND(HttpStatus.NOT_FOUND, "Объект или адрес не найден"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "HTTP-метод не поддерживается для этого адреса"),

    // ── 409: конфликт с состоянием данных ───────────────────────────────────
    CONFLICT(HttpStatus.CONFLICT, "Действие противоречит текущему состоянию данных"),
    EMAIL_EXISTS(HttpStatus.CONFLICT, "Пользователь с таким email уже есть (UC12)"),
    RULE_EXISTS(HttpStatus.CONFLICT, "Для показателя KPI уже есть правило (UC8)"),
    INSUFFICIENT_FUNDS(HttpStatus.CONFLICT, "Не хватает баллов на счёте (UC3, подтверждение корректировки)"),
    PROGRAM_INACTIVE(HttpStatus.CONFLICT, "Программа мотивации недоступна (UC3)"),
    OPERATION_STATE_CONFLICT(HttpStatus.CONFLICT, "Действие недопустимо в текущем статусе операции (UC7, контроль)"),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Данные одновременно изменил другой пользователь — повторите действие"),

    // ── 415 ─────────────────────────────────────────────────────────────────
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Ожидается тело application/json"),

    // ── 422: данные не прошли проверку ──────────────────────────────────────
    VALIDATION_FAILED(HttpStatus.UNPROCESSABLE_ENTITY, "Поля запроса не прошли проверку (Bean Validation или бизнес-правила)"),
    INVALID_CALCULATION_INPUT(HttpStatus.UNPROCESSABLE_ENTITY, "Некорректные входные данные расчёта бонуса (UC6)"),

    // ── 423: счёт заблокирован ──────────────────────────────────────────────
    ACCOUNT_BLOCKED(HttpStatus.LOCKED, "Бонусный счёт заблокирован (альт. поток UC1, UC3, UC5, UC7)"),

    // ── 500 ─────────────────────────────────────────────────────────────────
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Непредвиденная ошибка сервера");

    private final HttpStatus status;
    private final String description;

    ErrorCode(HttpStatus status, String description) {
        this.status = status;
        this.description = description;
    }

    public HttpStatus status() {
        return status;
    }

    public String description() {
        return description;
    }
}
