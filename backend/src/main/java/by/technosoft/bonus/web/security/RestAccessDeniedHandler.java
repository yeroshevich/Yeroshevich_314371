package by.technosoft.bonus.web.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import by.technosoft.bonus.web.error.ErrorCode;
import by.technosoft.bonus.web.error.ErrorResponseFactory;
import by.technosoft.bonus.web.error.SecurityMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Ответ 403 в едином JSON-формате, если отказ произошёл в цепочке фильтров безопасности. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final ErrorResponseFactory errors;

    public RestAccessDeniedHandler(ErrorResponseFactory errors) {
        this.errors = errors;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        errors.write(response, ErrorCode.ACCESS_DENIED, SecurityMessages.FORBIDDEN, request);
    }
}
