package by.technosoft.bonus.web;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * REST-слой целиком: JWT-аутентификация, ролевой доступ, единый формат ошибок.
 * Транзакция теста откатывается — данные БД не меняются.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RestApiSecurityTest {

    private static final String API = "/api/v1";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    private String login(String login) throws Exception {
        String body = mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + login + "@technosoft.by\",\"password\":\"demo\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).get("token").asText();
    }

    @Test
    void requestWithoutTokenGets401InCommonFormat() throws Exception {
        mvc.perform(get(API + "/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value(API + "/accounts/me"))
                .andExpect(jsonPath("$.timestamp").exists());
        mvc.perform(get(API + "/accounts/me").header("Authorization", "Bearer forged.token.value"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordAndBlockedUserAreRejected() throws Exception {
        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna.kovaleva@technosoft.by\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
        mvc.perform(post(API + "/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"gleb.sorokin@technosoft.by\",\"password\":\"demo\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_BLOCKED"));
    }

    @Test
    void employeeSeesOwnAccountButNotHrScreens() throws Exception {
        String employee = login("anna.kovaleva");
        mvc.perform(get(API + "/auth/me").header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("employee"))
                .andExpect(jsonPath("$.user.id").value("1"));
        mvc.perform(get(API + "/accounts/me").header("Authorization", employee))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account.employeeId").value("1"))
                .andExpect(jsonPath("$.history.length()").value(6));
        mvc.perform(get(API + "/rules").header("Authorization", employee))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mvc.perform(get(API + "/users").header("Authorization", employee))
                .andExpect(status().isForbidden());
    }

    @Test
    void financeCannotAccrueAndManagerCannotApprove() throws Exception {
        String accrual = "{\"employeeIds\":[\"3\"],\"amount\":500,\"basis\":\"project\",\"reason\":\"Релиз\"}";
        mvc.perform(post(API + "/accruals").header("Authorization", login("igor.zhuk"))
                        .contentType(MediaType.APPLICATION_JSON).content(accrual))
                .andExpect(status().isForbidden());
        mvc.perform(post(API + "/control/operations/131/approve").header("Authorization", login("dmitry.savitsky")))
                .andExpect(status().isForbidden());
    }

    @Test
    void validationAndDomainErrorsMapToStatusAndCode() throws Exception {
        String manager = login("dmitry.savitsky");
        mvc.perform(post(API + "/accruals").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeIds\":[],\"amount\":0,\"basis\":\"kpi\",\"reason\":\"\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post(API + "/accruals").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"employeeIds\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mvc.perform(post(API + "/accruals").header("Authorization", manager)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeIds\":[\"13\"],\"amount\":500,\"basis\":\"kpi\",\"reason\":\"Чужой\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        String employee = login("anna.kovaleva");
        mvc.perform(post(API + "/programs/6/redemptions").header("Authorization", employee))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.message").value(startsWith("Не хватает")));
        mvc.perform(get(API + "/accounts/me").header("Authorization", login("pavel.karpovich")))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_BLOCKED"));
    }

    /** Поиск без учёта регистра по кириллице (в БД правила сортировки «C»: lower() не меняет кириллицу). */
    @Test
    void operationSearchIsCaseInsensitiveForCyrillic() throws Exception {
        String hr = login("olga.melnik");
        mvc.perform(get(API + "/operations").header("Authorization", hr).param("q", "новик"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$[0].employeeName").value("Максим Новик"));
        mvc.perform(get(API + "/operations").header("Authorization", hr).param("q", "ОП-000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** Клиент пересчитывает итог «на лету» до ввода причины: расчёт без причины допустим. */
    @Test
    void previewDoesNotRequireReason() throws Exception {
        mvc.perform(post(API + "/accruals/preview").header("Authorization", login("dmitry.savitsky"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeIds\":[\"1\",\"3\"],\"amount\":800,\"basis\":\"kpi\",\"reason\":\"\",\"campaignId\":\"1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].coefficient").value(1.5))
                .andExpect(jsonPath("$.lines[0].total").value(1200))
                .andExpect(jsonPath("$.approvalCount").value(2));
    }

    @Test
    void accrualAboveLimitGoesToFinanceQueue() throws Exception {
        String body = mvc.perform(post(API + "/accruals").header("Authorization", login("dmitry.savitsky"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeIds\":[\"3\",\"4\"],\"amount\":1200,\"basis\":\"achievement\",\"reason\":\"Доклад\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pendingCount").value(2))
                .andExpect(jsonPath("$.operations[0].status").value("pending"))
                .andExpect(jsonPath("$.operations[0].createdBy").value("Дмитрий Савицкий"))
                .andReturn().getResponse().getContentAsString();
        String id = json.readTree(body).get("operations").get(0).get("id").asText();
        mvc.perform(post(API + "/control/operations/" + id + "/approve").header("Authorization", login("igor.zhuk")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accrued"));
    }
}
