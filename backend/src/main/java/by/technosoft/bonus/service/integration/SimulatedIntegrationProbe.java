package by.technosoft.bonus.service.integration;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.Integration;

/**
 * Проверка соединения на учебном стенде, где HRIS, ERP, AD и сервис уведомлений недоступны.
 * Проверяет то, что проверил бы и реальный клиент до сетевого вызова: адрес HTTPS (SEC-2)
 * с именем хоста и ключ API не короче 8 символов; время отклика детерминировано по адресу.
 */
@Component
public class SimulatedIntegrationProbe implements IntegrationProbe {

    static final int MIN_KEY_LENGTH = 8;

    @Override
    public ProbeResult check(Integration integration, String url, String apiKey) {
        URI uri = parse(url);
        if (uri == null || uri.getHost() == null || !"https".equals(uri.getScheme().toLowerCase(Locale.ROOT))) {
            return ProbeResult.failure("Сервер отклонил подключение: нужен адрес HTTPS");
        }
        if (apiKey == null || apiKey.trim().length() < MIN_KEY_LENGTH) {
            return ProbeResult.failure("Ключ не принят: " + integration.getName()
                    + " ожидает ключ не короче " + MIN_KEY_LENGTH + " символов");
        }
        return ProbeResult.success(60 + Math.floorMod(uri.getHost().hashCode(), 180));
    }

    private static URI parse(String url) {
        try {
            URI uri = new URI(url == null ? "" : url.trim());
            return uri.getScheme() == null ? null : uri;
        } catch (URISyntaxException e) {
            return null;
        }
    }
}
