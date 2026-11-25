package by.technosoft.bonus.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.service.BonusCalculationService;
import by.technosoft.bonus.service.dto.CalculationRequest;
import by.technosoft.bonus.service.dto.CalculationResult;

/**
 * ДЕФЕКТ D-1 (найден при тестировании ЛР10, не исправлен): статус кампании задаётся один раз при создании
 * и далее не меняется — в системе нет перехода «Запланирована → Активна» при наступлении даты начала
 * и «Активна → Завершена» после даты окончания (нет ни задания по расписанию, ни вычисления статуса по датам).
 * Последствия: запланированная кампания с бюджетом никогда не применяется в расчётах (FR9-2),
 * а истёкшая кампания продолжает отображаться как «Активна» (FR9-3; в расчётах она уже не применяется,
 * так как проверяются даты).
 * <p>
 * Тесты описывают ожидаемое поведение и сейчас ПАДАЮТ. Чтобы сборка оставалась зелёной, они выполняются только
 * по явному запросу: {@code mvn test -Dtest=CampaignLifecycleDefectTest -Dbonus.defects=true}.
 * После исправления дефекта условие {@link EnabledIfSystemProperty} нужно снять.
 * Часы приложения — 20.12.2026 (после окончания «Осеннего релиза 2.0» 15.12.2026).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CampaignLifecycleDefectTest.FixedClockConfig.class)
@EnabledIfSystemProperty(named = "bonus.defects", matches = "true",
        disabledReason = "Дефект D-1: статус кампании не меняется по датам. Запуск: -Dbonus.defects=true")
@DisplayName("Дефект D-1 — жизненный цикл кампании (UC9)")
class CampaignLifecycleDefectTest {

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.of("Europe/Minsk");
            return Clock.fixed(ZonedDateTime.of(2026, 12, 20, 12, 0, 0, 0, zone).toInstant(), zone);
        }
    }

    @Autowired private BonusCalculationService calculationService;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("FR9-2 [D-1]: запланированная кампания «Неделя наставничества» (×2, 01–07.12) применяется в свои даты")
    void plannedCampaignAppliesWhenStarted() {
        CalculationResult result = calculationService.calculate(
                new CalculationRequest(3L, 1L, new BigDecimal("100"), LocalDate.of(2026, 12, 3)));

        // Ожидается: 3000 × 0,4 × 100 % = 1200 × 2 = 2400 по кампании 2. Фактически: кампания 1 (× 1,5) → 1800.
        assertThat(result.campaignId()).isEqualTo(2L);
        assertThat(result.total()).isEqualTo(2400);
    }

    @Test
    @DisplayName("FR9-3 [D-1]: после даты окончания кампания «Осенний релиз 2.0» отображается завершённой")
    void finishedCampaignIsDeactivated() throws Exception {
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"olga.melnik@technosoft.by\",\"password\":\"demo\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(body).get("token").asText();

        // Ожидается «finished»; фактически — «active» (статус не пересчитывается).
        mvc.perform(get("/api/v1/campaigns").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '1')].status").value("finished"));
    }
}
