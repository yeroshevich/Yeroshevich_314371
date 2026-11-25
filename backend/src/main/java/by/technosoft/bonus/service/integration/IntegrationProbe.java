package by.technosoft.bonus.service.integration;

import by.technosoft.bonus.domain.Integration;

/**
 * Порт проверки соединения с внешней системой (UC13). {@link by.technosoft.bonus.service.admin.IntegrationAdminService}
 * зависит только от этого интерфейса; реальный HTTP-клиент подключается новой реализацией
 * без изменения сервиса (OCP, DIP).
 */
@FunctionalInterface
public interface IntegrationProbe {

    /**
     * @param integration подключение (вид и название системы)
     * @param url         проверяемый адрес
     * @param apiKey      проверяемый ключ API (открытым текстом, не сохраняется)
     */
    ProbeResult check(Integration integration, String url, String apiKey);

    /**
     * Итог проверки.
     *
     * @param latencyMs время отклика, мс; {@code null} — соединение не установлено
     */
    record ProbeResult(boolean ok, String message, Integer latencyMs) {

        public static ProbeResult success(int latencyMs) {
            return new ProbeResult(true, "Соединение установлено", latencyMs);
        }

        public static ProbeResult failure(String message) {
            return new ProbeResult(false, message, null);
        }
    }
}
