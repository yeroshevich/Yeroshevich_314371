package by.technosoft.bonus.web.error;

/** Тексты ответов 401/403 — одинаковые в фильтрах безопасности и в обработчике ошибок (DRY). */
public final class SecurityMessages {

    public static final String UNAUTHORIZED = "Сессия закончилась. Войдите снова.";
    public static final String FORBIDDEN = "Для этого действия у вашей роли нет прав.";

    private SecurityMessages() {
    }
}
