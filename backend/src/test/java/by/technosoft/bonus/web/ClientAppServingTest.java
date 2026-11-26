package by.technosoft.bonus.web;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Раздача клиента сервером (дистрибутив, ЛР 11): маршруты SPA открываются при обновлении страницы,
 * файлы клиента доступны без токена, API по-прежнему защищено, неизвестный адрес API — 404 в едином формате.
 * Вместо собранного клиента используется тестовая папка classpath:/spa-test/ (index.html и assets/app-test.js).
 */
@SpringBootTest(properties = "spring.web.resources.static-locations=classpath:/spa-test/")
@AutoConfigureMockMvc
@Transactional
class ClientAppServingTest {

    private static final String INDEX_MARKER = "spa-test-index";

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;

    @ParameterizedTest
    @ValueSource(strings = {"/me", "/history", "/admin/users", "/accruals/new", "/login"})
    void clientRoutesOpenIndexWithoutToken(String route) throws Exception {
        mvc.perform(get(route))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(INDEX_MARKER)))
                .andExpect(header().string("Cache-Control", containsString("no-cache")));
    }

    @Test
    void hashedAssetsAreServedAndCachedForLong() throws Exception {
        mvc.perform(get("/assets/app-test.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("spa-test-asset")))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")));
    }

    @Test
    void missingFilesAreNotReplacedWithIndex() throws Exception {
        mvc.perform(get("/assets/missing.js")).andExpect(status().isNotFound());
        mvc.perform(get("/logo.png"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(INDEX_MARKER))));
    }

    @Test
    void apiStaysProtectedAndUnknownApiAddressIsJson404() throws Exception {
        mvc.perform(get("/api/v1/accounts/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        String body = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"anna.kovaleva@technosoft.by\",\"password\":\"demo\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + json.readTree(body).get("token").asText();
        mvc.perform(get("/api/v1/no-such-endpoint").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(content().string(not(containsString(INDEX_MARKER))));
    }

    @Test
    void apiDocumentationIsOpen() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("TechnoSoft Bonus REST API"));
    }
}
