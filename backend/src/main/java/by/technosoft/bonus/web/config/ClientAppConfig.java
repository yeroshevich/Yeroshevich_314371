package by.technosoft.bonus.web.config;

import java.time.Duration;
import java.util.Arrays;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Раздача собранного клиента (SPA) тем же сервером, что и REST API: один процесс, один порт (CON-1).
 * Файлы клиента берутся из {@code spring.web.resources.static-locations} — по умолчанию
 * {@code classpath:/static/}, куда клиент встраивается при сборке дистрибутива (профиль Maven {@code dist});
 * при необходимости можно указать внешнюю папку ({@code file:./web/}) без пересборки.
 * <ul>
 *   <li>{@code /assets/**} — файлы с хешем в имени: кэшируются браузером на год;</li>
 *   <li>остальные пути — файл, если он есть, иначе {@code index.html} для маршрутов клиента
 *       ({@code /me}, {@code /admin/users} открываются при обновлении страницы), см. {@link SpaFallbackResourceResolver};
 *       {@code index.html} не кэшируется, чтобы после обновления версии браузер получил новые ссылки на файлы.</li>
 * </ul>
 * Настройка выполняется раньше стандартной Spring Boot ({@link Ordered#HIGHEST_PRECEDENCE}), поэтому шаблон
 * {@code /**} принадлежит ей, а стандартный обработчик статических ресурсов не регистрируется.
 */
@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ClientAppConfig implements WebMvcConfigurer {

    static final String ASSETS = "assets/";

    private final String[] locations;

    public ClientAppConfig(WebProperties webProperties) {
        this.locations = Arrays.stream(webProperties.getResources().getStaticLocations())
                .map(location -> location.endsWith("/") ? location : location + "/")
                .toArray(String[]::new);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/" + ASSETS + "**")
                .addResourceLocations(Arrays.stream(locations).map(l -> l + ASSETS).toArray(String[]::new))
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
        registry.addResourceHandler("/**")
                .addResourceLocations(locations)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new SpaFallbackResourceResolver());
    }
}
