package by.technosoft.bonus.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import by.technosoft.bonus.domain.BusinessEvent;
import by.technosoft.bonus.domain.Operation;
import by.technosoft.bonus.repository.BusinessEventRepository;
import by.technosoft.bonus.repository.EmployeeRepository;
import by.technosoft.bonus.repository.IntegrationRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.EventProcessingService;
import by.technosoft.bonus.service.dto.EventOutcome;
import by.technosoft.bonus.service.dto.EventProcessingResult;

/**
 * Интеграционная проверка функциональных требований, которые полностью видны только через REST API и БД
 * (фильтрация по датам в запросе к БД, сохранение и повторное чтение, формат ответа).
 * Демо-данные V2; каждый тест — в транзакции, которая откатывается (данные БД не меняются).
 * Часы зафиксированы на 25.11.2026 12:00 (Минск).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(RequirementsRestApiTest.FixedClockConfig.class)
@DisplayName("REST API — функциональные требования на демо-данных (интеграционные)")
class RequirementsRestApiTest {

    private static final String API = "/api/v1";

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            ZoneId zone = ZoneId.of("Europe/Minsk");
            return Clock.fixed(ZonedDateTime.of(2026, 11, 25, 12, 0, 0, 0, zone).toInstant(), zone);
        }
    }

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private EventProcessingService eventProcessingService;
    @Autowired private BusinessEventRepository eventRepository;
    @Autowired private IntegrationRepository integrationRepository;
    @Autowired private EmployeeRepository employeeRepository;
    @Autowired private OperationRepository operationRepository;

    private String login(String email) throws Exception {
        String body = mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"demo\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    private String as(String login) throws Exception {
        return login(login + "@technosoft.by");
    }

    private ResultActions getAs(String token, String url) throws Exception {
        return mvc.perform(get(API + url).header("Authorization", token));
    }

    private ResultActions send(String method, String token, String url, String body) throws Exception {
        var builder = switch (method) {
            case "POST" -> post(API + url);
            case "PUT" -> put(API + url);
            default -> patch(API + url);
        };
        return mvc.perform(builder.header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(body == null ? "" : body));
    }

    @Test
    @DisplayName("FR1-1: авторизованный сотрудник получает текущий баланс своего счёта")
    void balanceOfAuthorizedEmployee() throws Exception {
        getAs(as("anna.kovaleva"), "/accounts/me")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.employeeId").value("1"))
                .andExpect(jsonPath("$.account.balance").value(8900))
                .andExpect(jsonPath("$.account.status").value("active"));
    }

    @Test
    @DisplayName("FR1-2: если баланс недоступен (счёт заблокирован) — 423 и сообщение об ошибке")
    void unavailableBalanceShowsError() throws Exception {
        getAs(as("pavel.karpovich"), "/accounts/me")
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"))
                .andExpect(jsonPath("$.message").value("Баланс недоступен, обратитесь к HR."));
    }

    @Test
    @DisplayName("FR3-1, FR3-3, FR1-3: списание при достаточном балансе; после него отображается обновлённый баланс")
    void redemptionUpdatesShownBalance() throws Exception {
        String anna = as("anna.kovaleva");
        send("POST", anna, "/programs/2/redemptions", null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.operation.type").value("writeoff"))
                .andExpect(jsonPath("$.operation.amount").value(-900))
                .andExpect(jsonPath("$.account.balance").value(8000));
        getAs(anna, "/accounts/me").andExpect(jsonPath("$.account.balance").value(8000));
    }

    @Test
    @DisplayName("FR3-2: при недостаточном балансе списание отклоняется (409), баланс не меняется")
    void insufficientBalanceRejected() throws Exception {
        String anna = as("anna.kovaleva");
        send("POST", anna, "/programs/6/redemptions", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").value(startsWith("Не хватает")));
        getAs(anna, "/accounts/me").andExpect(jsonPath("$.account.balance").value(8900));
    }

    @Test
    @DisplayName("FR2-1, FR2-3: история операций фильтруется по датам периода (включительно)")
    void historyIsFilteredByPeriod() throws Exception {
        String anna = as("anna.kovaleva");
        getAs(anna, "/accounts/me/operations").andExpect(jsonPath("$.length()").value(7));
        getAs(anna, "/accounts/me/operations?from=2026-11-01&to=2026-11-30")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].employeeId", everyItem(org.hamcrest.Matchers.is("1"))));
        getAs(anna, "/accounts/me/operations?from=2026-11-20&to=2026-11-20")
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].number").value("ОП-000137"));
    }

    @Test
    @DisplayName("FR2-2: если операций за период нет — пустой список")
    void emptyHistory() throws Exception {
        getAs(as("anna.kovaleva"), "/accounts/me/operations?from=2026-11-21&to=2026-11-22")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("FR5-1, FR5-2, FR5-3: руководитель начисляет баллы своей команде, баланс получателя растёт")
    void managerAccrualUpdatesBalance() throws Exception {
        send("POST", as("dmitry.savitsky"), "/accruals",
                "{\"employeeIds\":[\"1\"],\"amount\":500,\"basis\":\"project\",\"reason\":\"Релиз платформы\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accruedCount").value(1))
                .andExpect(jsonPath("$.operations[0].amount").value(500))
                .andExpect(jsonPath("$.operations[0].status").value("accrued"));
        getAs(as("anna.kovaleva"), "/accounts/me").andExpect(jsonPath("$.account.balance").value(9400));
    }

    @Test
    @DisplayName("FR7-1, FR7-2, FR7-3: корректировка HR сохраняется, баланс пересчитывается, изменение видно в журнале аудита")
    void correctionIsJournaled() throws Exception {
        long account = operationRepository.findById(14L).map(Operation::getAccount).orElseThrow().getEmployee().getId();
        send("PUT", as("olga.melnik"), "/operations/14",
                "{\"action\":\"adjust\",\"newAmount\":700,\"reason\":\"Ошибка в сумме\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("correction"))
                .andExpect(jsonPath("$.originalAmount").value(1000))
                .andExpect(jsonPath("$.status").value("corrected"));
        getAs(as("sergey.litvinov"), "/audit?from=2026-11-25&to=2026-11-25")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItem("Корректировка")));
        assertThat(account).isEqualTo(6L);
    }

    @Test
    @DisplayName("FR8-1, FR8-2: HR создаёт правило, оно сохраняется и отображается в списке правил")
    void hrCreatesRule() throws Exception {
        String hr = as("olga.melnik");
        send("POST", hr, "/rules", "{\"name\":\"Отзывы клиентов\",\"kpi\":\"Индекс удовлетворённости клиентов\","
                + "\"weight\":0.25,\"basePoints\":4000,\"periodicity\":\"quarter\",\"threshold\":85,\"active\":true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deferred").value(false))
                .andExpect(jsonPath("$.effectiveFrom").value("2026-11-25"));
        getAs(hr, "/rules").andExpect(jsonPath("$[*].name", hasItem("Отзывы клиентов")));
        send("POST", as("dmitry.savitsky"), "/rules", "{\"name\":\"X\",\"kpi\":\"Y\",\"weight\":0.5,"
                + "\"basePoints\":100,\"periodicity\":\"month\",\"threshold\":10,\"active\":true}")
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR9-1: кампания сохраняется с параметрами; без бюджета — статус «Требуется утверждение»")
    void campaignIsSaved() throws Exception {
        String hr = as("olga.melnik");
        send("POST", hr, "/campaigns", "{\"name\":\"Зимний спринт качества\",\"description\":\"QA\","
                + "\"startDate\":\"2027-01-01\",\"endDate\":\"2027-01-31\",\"departmentIds\":[\"2\"],"
                + "\"coefficient\":2,\"budget\":null}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("needs_approval"))
                .andExpect(jsonPath("$.departmentIds[0]").value("2"));
        getAs(hr, "/campaigns").andExpect(jsonPath("$[*].name", hasItem("Зимний спринт качества")));
    }

    @Test
    @DisplayName("FR10-1, FR10-2, FR10-3: отчёт за период включает только операции этого периода и выдаётся готовым")
    void reportByPeriod() throws Exception {
        String hr = as("olga.melnik");
        getAs(hr, "/reports/employees?from=2026-11-20&to=2026-11-20")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("employees"))
                .andExpect(jsonPath("$.from").value("2026-11-20"))
                .andExpect(jsonPath("$.generatedAt").exists())
                .andExpect(jsonPath("$.rows[?(@.name == 'Анна Ковалёва')].operations", hasItem(1)));
        getAs(hr, "/reports/departments?from=2025-01-01&to=2025-01-31")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    @Test
    @DisplayName("FR11-1, FR11-2, FR11-3: дашборд эффективности отображается HR с метриками и сравнением периодов")
    void analyticsDashboard() throws Exception {
        getAs(as("olga.melnik"), "/analytics/dashboard?period=quarter")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("quarter"))
                .andExpect(jsonPath("$.averageBonus.current").isNumber())
                .andExpect(jsonPath("$.byDepartment.length()").value(6))
                .andExpect(jsonPath("$.monthly.length()").value(6));
        getAs(as("anna.kovaleva"), "/analytics/dashboard").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("FR12-1, FR12-3: администратор создаёт пользователя (дубль email — 409) и деактивирует его — вход закрыт")
    void userLifecycle() throws Exception {
        String admin = as("sergey.litvinov");
        String user = "{\"fullName\":\"Марина Захарова\",\"email\":\"marina.zakharova@technosoft.by\","
                + "\"departmentId\":\"2\",\"position\":\"QA-инженер\",\"role\":\"employee\"}";
        String body = send("POST", admin, "/users", user)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("active"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("id").asText();
        send("POST", admin, "/users", user)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_EXISTS"));
        login("marina.zakharova@technosoft.by");

        send("PATCH", admin, "/users/" + id + "/status", "{\"status\":\"blocked\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("blocked"));
        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"marina.zakharova@technosoft.by\",\"password\":\"demo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    }

    @Test
    @DisplayName("FR12-2: администратор меняет роль пользователя")
    void roleIsChanged() throws Exception {
        send("PUT", as("sergey.litvinov"), "/users/3", "{\"fullName\":\"Максим Новик\","
                + "\"email\":\"maxim.novik@technosoft.by\",\"departmentId\":\"1\",\"position\":\"Тимлид\","
                + "\"role\":\"manager\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("manager"));
    }

    @Test
    @DisplayName("FR13-1, FR13-3: параметры интеграции сохраняются только после успешной проверки; затем включается синхронизация")
    void integrationSettings() throws Exception {
        String admin = as("sergey.litvinov");
        send("PUT", admin, "/integrations/2", "{\"url\":\"http://erp.local\",\"apiKey\":\"erp-51c0aa-2026\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("нужен адрес HTTPS")));
        send("PUT", admin, "/integrations/2", "{\"url\":\"https://erp.technosoft.by/api\",\"apiKey\":\"erp-51c0aa-2026\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://erp.technosoft.by/api"))
                .andExpect(jsonPath("$.apiKey").value("••••••••"))
                .andExpect(jsonPath("$.lastCheck.ok").value(true));
        send("PUT", admin, "/integrations/2", "{\"enabled\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.lastSyncAt").exists());
    }

    @Test
    @DisplayName("FR13-2: внешнее событие ERP «этап принят» обрабатывается и даёт начисление сотруднику")
    void externalEventIsProcessed() {
        BusinessEvent event = eventRepository.save(BusinessEvent.received(integrationRepository.getReferenceById(2L),
                "ERP-STAGE-LR10", "stage_accepted", employeeRepository.getReferenceById(3L), "{\"project\":\"PRJ-7\"}",
                OffsetDateTime.parse("2026-11-24T12:00:00+03:00")));

        EventProcessingResult result = eventProcessingService.process(event.getId());

        assertThat(result.outcome()).isEqualTo(EventOutcome.ACCRUED);
        // 6000 × 0,5 = 3000; отдел разработки в кампании «Осенний релиз 2.0» (× 1,5) → 4500
        assertThat(operationRepository.findById(result.operationId()).orElseThrow().getAmount()).isEqualTo(4500);
    }
}
