package by.technosoft.bonus.web.security;

import java.io.IOException;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import by.technosoft.bonus.web.error.ErrorCode;
import by.technosoft.bonus.web.error.ErrorResponseFactory;
import by.technosoft.bonus.web.error.SecurityMessages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Ответ 401 в едином JSON-формате для запросов без действительного токена. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ErrorResponseFactory errors;

    public RestAuthenticationEntryPoint(ErrorResponseFactory errors) {
        this.errors = errors;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        errors.write(response, ErrorCode.UNAUTHORIZED,
                reason instanceof String text ? text : SecurityMessages.UNAUTHORIZED, request);
    }
}
