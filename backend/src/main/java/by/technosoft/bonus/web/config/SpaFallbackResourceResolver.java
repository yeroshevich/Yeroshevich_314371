package by.technosoft.bonus.web.config;

import java.io.IOException;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Поиск файла клиента с возвратом {@code index.html} для маршрутов SPA (React Router, BrowserRouter).
 * Существующий файл отдаётся как есть. Для отсутствующего пути {@code index.html} возвращается, только если это
 * маршрут клиента: не запрос к API ({@code /api/…}) или к документации API и без расширения в последнем
 * сегменте ({@code /me}, {@code /admin/users}). Отсутствующий файл ({@code /logo.png}) и неизвестный адрес API
 * остаются ошибкой 404 в едином формате ({@code GlobalExceptionHandler}); если клиент не собран (нет
 * {@code index.html}), маршруты клиента тоже дают 404.
 */
public class SpaFallbackResourceResolver extends PathResourceResolver {

    static final String INDEX = "index.html";

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        Resource resource = super.getResource(resourcePath, location);
        if (resource != null || !isClientRoute(resourcePath)) {
            return resource;
        }
        return super.getResource(INDEX, location);
    }

    /** Путь (без ведущего «/») может быть маршрутом клиента, а не файлом и не адресом сервера. */
    static boolean isClientRoute(String path) {
        String p = path.startsWith("/") ? path.substring(1) : path;
        if (p.equals("api") || p.startsWith("api/") || p.startsWith("v3/") || p.startsWith("swagger-ui")) {
            return false;
        }
        String last = p.substring(p.lastIndexOf('/') + 1);
        return !last.contains(".");
    }
}
