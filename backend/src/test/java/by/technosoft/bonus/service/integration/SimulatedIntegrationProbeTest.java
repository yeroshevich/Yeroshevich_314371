package by.technosoft.bonus.service.integration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import by.technosoft.bonus.domain.Integration;
import by.technosoft.bonus.domain.IntegrationKind;
import by.technosoft.bonus.domain.TestEntities;
import by.technosoft.bonus.service.integration.IntegrationProbe.ProbeResult;

/** Проверка параметров подключения к внешней системе (UC13, SEC-2). */
@DisplayName("SimulatedIntegrationProbe — проверка соединения")
class SimulatedIntegrationProbeTest {

    private final SimulatedIntegrationProbe probe = new SimulatedIntegrationProbe();
    private final Integration hris = TestEntities.integration(1, IntegrationKind.HRIS, "HRIS",
            "https://hris.technosoft.by/api/v2", "enc", true);

    @Test
    @DisplayName("FR13-1: адрес HTTPS с именем хоста и ключ от 8 символов — соединение установлено")
    void httpsAndKeyPass() {
        ProbeResult result = probe.check(hris, "HTTPS://hris.technosoft.by/api/v2", "12345678");

        assertThat(result.ok()).isTrue();
        assertThat(result.message()).isEqualTo("Соединение установлено");
        assertThat(result.latencyMs()).isBetween(60, 239);
        assertThat(probe.check(hris, "https://hris.technosoft.by/api/v2", "12345678").latencyMs())
                .isEqualTo(result.latencyMs());
    }

    @ParameterizedTest(name = "«{0}»")
    @NullAndEmptySource
    @ValueSource(strings = {"http://hris.technosoft.by", "ftp://hris.technosoft.by", "hris.technosoft.by",
            "https://", "https:///path", "не адрес"})
    @DisplayName("FR13-1: адрес не HTTPS или без хоста отклоняется — «нужен адрес HTTPS»")
    void nonHttpsRejected(String url) {
        ProbeResult result = probe.check(hris, url, "12345678");

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo("Сервер отклонил подключение: нужен адрес HTTPS");
        assertThat(result.latencyMs()).isNull();
    }

    @ParameterizedTest(name = "«{0}»")
    @NullAndEmptySource
    @ValueSource(strings = {"1234567", "   1234567   "})
    @DisplayName("FR13-1: ключ API короче 8 символов отклоняется")
    void shortKeyRejected(String key) {
        ProbeResult result = probe.check(hris, "https://hris.technosoft.by", key);

        assertThat(result.ok()).isFalse();
        assertThat(result.message()).isEqualTo("Ключ не принят: HRIS ожидает ключ не короче 8 символов");
    }
}
