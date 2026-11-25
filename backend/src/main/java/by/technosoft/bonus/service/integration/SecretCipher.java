package by.technosoft.bonus.service.integration;

import java.util.Optional;

/**
 * Порт шифрования секретов (API-ключей интеграций, DATA-4). Сервисы зависят от интерфейса,
 * алгоритм подставляется через DI (DIP); узкий контракт из двух методов (ISP).
 */
public interface SecretCipher {

    /** Шифрует секрет для хранения в БД. */
    String encrypt(String plain);

    /**
     * Расшифровывает сохранённое значение.
     *
     * @return секрет или пусто, если значение записано не этим шифром (например, демо-данные)
     */
    Optional<String> decrypt(String stored);
}
