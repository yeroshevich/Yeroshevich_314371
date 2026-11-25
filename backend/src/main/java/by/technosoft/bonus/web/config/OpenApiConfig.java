package by.technosoft.bonus.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Документация API в формате OpenAPI 3: {@code /v3/api-docs} (JSON) и {@code /swagger-ui.html}.
 * По умолчанию все операции требуют JWT; вход помечен как открытый.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI bonusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TechnoSoft Bonus REST API")
                        .version("v1")
                        .description("""
                                Серверная часть ПС управления бонусной системой сотрудников (Spring Boot 3, CON-3).
                                JSON, даты ISO 8601 (DATA-3), суммы — целые баллы, идентификаторы — строки.
                                Аутентификация: POST /api/v1/auth/login → JWT; далее заголовок Authorization: Bearer <token>.
                                Ошибки — единый формат ApiError {status, code, message, path, timestamp}."""))
                .addServersItem(new Server().url("http://localhost:8080").description("Локальный сервер"))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
