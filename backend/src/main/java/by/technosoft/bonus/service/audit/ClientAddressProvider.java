package by.technosoft.bonus.service.audit;

import java.net.InetAddress;
import java.util.Optional;

/**
 * Порт: IP-адрес клиента текущего запроса для журнала аудита (SEC-4).
 * Бизнес-логика не знает о HTTP; реализацию предоставляет REST-слой (DIP).
 */
@FunctionalInterface
public interface ClientAddressProvider {

    /** Адрес клиента или пусто (фоновая задача, тест). */
    Optional<InetAddress> currentAddress();
}
