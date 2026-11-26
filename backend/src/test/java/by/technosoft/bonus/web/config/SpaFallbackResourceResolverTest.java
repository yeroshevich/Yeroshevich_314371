package by.technosoft.bonus.web.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Какие пути считаются маршрутами клиента (SPA) и получают index.html, а какие — нет. */
class SpaFallbackResourceResolverTest {

    @ParameterizedTest
    @ValueSource(strings = {"", "me", "/me", "history", "admin/users", "admin/audit", "accruals/new", "login"})
    void clientRoutesFallBackToIndex(String path) {
        assertThat(SpaFallbackResourceResolver.isClientRoute(path)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "api/v1/unknown", "/api/v1/accounts/me", "v3/api-docs", "swagger-ui/index.html",
            "swagger-ui.html", "logo.png", "assets/missing.js", "admin/report.csv"})
    void filesAndServerAddressesAreNotClientRoutes(String path) {
        assertThat(SpaFallbackResourceResolver.isClientRoute(path)).isFalse();
    }
}
