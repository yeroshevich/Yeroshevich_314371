package by.technosoft.bonus.service.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import by.technosoft.bonus.config.SecurityProperties;

/** Шифрование ключей API интеграций (UC13, DATA-4): AES-256-GCM со случайным вектором инициализации. */
@DisplayName("AesGcmSecretCipher — шифрование ключей интеграций")
class AesGcmSecretCipherTest {

    private static final String KEY = "dGVjaG5vc29mdC1ib251cy1kZXYta2V5LTMyLWJ5dGU=";

    private static AesGcmSecretCipher cipher(String base64Key) {
        return new AesGcmSecretCipher(new SecurityProperties(null, base64Key, "demo", List.of()));
    }

    private final AesGcmSecretCipher cipher = cipher(KEY);

    @Test
    @DisplayName("FR13-1: сохранённый ключ интеграции расшифровывается в исходное значение")
    void roundTrip() {
        String stored = cipher.encrypt("erp-51c0aa-2026");

        assertThat(stored).startsWith(AesGcmSecretCipher.PREFIX).doesNotContain("erp-51c0aa-2026");
        assertThat(cipher.decrypt(stored)).contains("erp-51c0aa-2026");
        assertThat(cipher.decrypt(cipher.encrypt("ключ-кириллицей"))).contains("ключ-кириллицей");
    }

    @Test
    @DisplayName("FR13-1: один и тот же ключ шифруется каждый раз по-разному (случайный вектор инициализации)")
    void randomIv() {
        assertThat(cipher.encrypt("same-key-123")).isNotEqualTo(cipher.encrypt("same-key-123"));
    }

    @Test
    @DisplayName("FR13-1: изменённое, чужое или незашифрованное значение не расшифровывается")
    void tamperedOrForeignValues() {
        String stored = cipher.encrypt("erp-51c0aa-2026");
        byte[] raw = Base64.getDecoder().decode(stored.substring(AesGcmSecretCipher.PREFIX.length()));
        raw[raw.length - 1] ^= 1;
        String tampered = AesGcmSecretCipher.PREFIX + Base64.getEncoder().encodeToString(raw);

        assertThat(cipher.decrypt(tampered)).isEmpty();
        assertThat(cipher.decrypt("demo-erp-key-2026")).isEmpty();
        assertThat(cipher.decrypt(null)).isEmpty();
        assertThat(cipher.decrypt(AesGcmSecretCipher.PREFIX + "не base64")).isEmpty();
        String otherKey = Base64.getEncoder().encodeToString(new byte[32]);
        assertThat(cipher(otherKey).decrypt(stored)).isEmpty();
    }

    @Test
    @DisplayName("FR13-1: ключ шифрования должен быть длиной 32 байта")
    void keyLengthIsChecked() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> cipher(shortKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32 байта");
    }
}
