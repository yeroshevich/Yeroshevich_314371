package by.technosoft.bonus.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.domain.AuditLog;
import by.technosoft.bonus.domain.CampaignStatus;
import by.technosoft.bonus.repository.AuditLogRepository;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.service.BonusCalculationService;
import by.technosoft.bonus.service.campaign.CampaignLifecycleService;
import by.technosoft.bonus.service.campaign.CampaignLifecycleService.StatusChange;
import by.technosoft.bonus.service.dto.CalculationRequest;
import by.technosoft.bonus.service.dto.CalculationResult;

/**
 * Жизненный цикл бонусной кампании на демо-данных (UC9, FR9-2, FR9-3) — повторное тестирование после
 * исправления дефекта D-1 (ранее {@code CampaignLifecycleDefectTest}, выполнялся только с {@code -Dbonus.defects=true}).
 * Статусы кампаний меняет {@link CampaignLifecycleService}: ежедневно и при старте приложения
 * ({@code CampaignLifecycleSchedulingConfig}), перед автоматической обработкой событий и перед чтением списка кампаний.
 * <p>
 * Каждый тест — в транзакции, которая откатывается. Запуск по расписанию и при старте в тестах отключён
 * ({@code src/test/resources/config/application.yml}), поэтому синхронизация выполняется только в транзакции теста.
 * Часы приложения переставляются в каждом тесте: статус кампании — это статус на «сегодня».
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(CampaignLifecycleTest.MovableClockConfig.class)
@DisplayName("Жизненный цикл кампании по датам (UC9, исправление D-1, интеграционные)")
class CampaignLifecycleTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Minsk");

    /** Часы приложения, которые тест переставляет на нужную дату. */
    static final class MovableClock extends Clock {
        private volatile Instant instant = ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, ZONE).toInstant();

        void set(int year, int month, int day, int hour, int minute) {
            instant = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    @TestConfiguration
    static class MovableClockConfig {
        @Bean
        @Primary
        MovableClock movableClock() {
            return new MovableClock();
        }
    }

    @Autowired private MovableClock clock;
    @Autowired private CampaignLifecycleService campaignLifecycleService;
    @Autowired private BonusCalculationService calculationService;
    @Autowired private CampaignRepository campaignRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @Test
    @DisplayName("FR9-2 [D-1]: запланированная кампания «Неделя наставничества» (×2, 01–07.12) применяется в свои даты")
    void plannedCampaignAppliesWhenStarted() {
        assertThat(campaignRepository.findById(2L).orElseThrow().getStatus()).isEqualTo(CampaignStatus.PLANNED);

        // 03.12.2026 00:00 — ежедневный запуск (CampaignLifecycleSchedulingConfig#synchronizeDaily).
        clock.set(2026, 12, 3, 0, 0);
        List<StatusChange> changes = campaignLifecycleService.synchronizeToday();

        // «Осенний релиз 2.0» идёт до 15.12 и остаётся активным; «Квартал без инцидентов» ждёт утверждения.
        assertThat(changes).containsExactly(
                new StatusChange(2L, "Неделя наставничества", CampaignStatus.PLANNED, CampaignStatus.ACTIVE));
        assertThat(campaignRepository.findById(2L).orElseThrow().getStatus()).isEqualTo(CampaignStatus.ACTIVE);
        assertThat(campaignRepository.findById(3L).orElseThrow().getStatus())
                .isEqualTo(CampaignStatus.NEEDS_APPROVAL);

        clock.set(2026, 12, 3, 12, 0);
        CalculationResult result = calculationService.calculate(
                new CalculationRequest(3L, 1L, new BigDecimal("100"), LocalDate.of(2026, 12, 3)));

        // 3000 × 0,4 × 100 % = 1200 × 2 = 2400 по кампании 2 (у кампании 1 коэффициент ниже — × 1,5).
        assertThat(result.campaignId()).isEqualTo(2L);
        assertThat(result.total()).isEqualTo(2400);

        List<AuditLog> audit = auditLogRepository.findByObjectTypeAndObjectIdOrderByOccurredAtDesc("campaign", 2L);
        assertThat(audit).first().satisfies(a -> assertStatusChangeAudited(a, "planned", "active"));
    }

    @Test
    @DisplayName("FR9-3 [D-1]: после даты окончания кампания «Осенний релиз 2.0» отображается завершённой")
    void finishedCampaignIsDeactivated() throws Exception {
        clock.set(2026, 12, 20, 12, 0);
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"olga.melnik@technosoft.by\",\"password\":\"demo\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(body).get("token").asText();

        // Список кампаний читается с актуальным статусом: истёкшие завершены, «Требуется утверждение» не меняется.
        mvc.perform(get("/api/v1/campaigns").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '1')].status").value("finished"))
                .andExpect(jsonPath("$[?(@.id == '2')].status").value("finished"))
                .andExpect(jsonPath("$[?(@.id == '3')].status").value("needs_approval"))
                .andExpect(jsonPath("$[?(@.id == '4')].status").value("finished"));

        assertThat(campaignRepository.findById(1L).orElseThrow().getStatus()).isEqualTo(CampaignStatus.FINISHED);
        assertThat(auditLogRepository.findByObjectTypeAndObjectIdOrderByOccurredAtDesc("campaign", 1L))
                .anySatisfy(a -> assertStatusChangeAudited(a, "active", "finished"));

        // Повторное чтение в тот же день ничего не меняет и не дублирует записи журнала.
        long auditBefore = auditLogRepository.count();
        mvc.perform(get("/api/v1/campaigns").header("Authorization", token)).andExpect(status().isOk());
        assertThat(auditLogRepository.count()).isEqualTo(auditBefore);
    }

    /** Запись журнала: действие системы (пользователь не указан) с прежним и новым статусом. */
    private void assertStatusChangeAudited(AuditLog entry, String from, String to) {
        assertThat(entry.getAction()).isEqualTo("Изменение статуса кампании");
        assertThat(entry.getUser()).isNull();
        JsonNode details;
        try {
            details = json.readTree(entry.getDetails());
        } catch (JsonProcessingException e) {
            throw new AssertionError("details не JSON: " + entry.getDetails(), e);
        }
        assertThat(details.get("from").asText()).isEqualTo(from);
        assertThat(details.get("to").asText()).isEqualTo(to);
    }
}
