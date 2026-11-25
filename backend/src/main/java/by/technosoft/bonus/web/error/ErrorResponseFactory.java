package by.technosoft.bonus.web.error;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Сборка ответа об ошибке в едином формате (DRY): используется и обработчиком исключений
 * контроллеров, и фильтрами безопасности, которые работают до DispatcherServlet.
 */
@Component
public class ErrorResponseFactory {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ErrorResponseFactory(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ResponseEntity<ApiErrorResponse> entity(HttpStatus status, String code, String message,
                                                   HttpServletRequest request) {
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body(body(status, code, message, request));
    }

    public ResponseEntity<ApiErrorResponse> entity(ErrorCode code, String message, HttpServletRequest request) {
        return entity(code.status(), code.name(), message, request);
    }

    /** Запись ответа напрямую в сервлетный ответ — для фильтров безопасности. */
    public void write(HttpServletResponse response, ErrorCode code, String message, HttpServletRequest request)
            throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body(code.status(), code.name(), message, request));
    }

    private ApiErrorResponse body(HttpStatus status, String code, String message, HttpServletRequest request) {
        return new ApiErrorResponse(status.value(), code, message, request.getRequestURI(), OffsetDateTime.now(clock));
    }
}
