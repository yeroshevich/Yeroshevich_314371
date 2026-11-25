package by.technosoft.bonus.web.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.service.auth.AuthenticationService;
import by.technosoft.bonus.service.exception.AccessDeniedDomainException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Аутентификация по заголовку {@code Authorization: Bearer <JWT>} (SEC-1). Токен проверяется
 * {@link TokenService}; учётная запись перечитывается из БД, поэтому заблокированный пользователь
 * и сменённая роль вступают в силу сразу, не дожидаясь истечения токена.
 * Без токена или с неверным токеном запрос остаётся анонимным — ответ 401 формирует
 * {@link RestAuthenticationEntryPoint}, если адрес требует входа.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Атрибут запроса с причиной отказа — её показывает {@link RestAuthenticationEntryPoint}. */
    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";

    private static final String BEARER = "Bearer ";

    private final TokenService tokenService;
    private final AuthenticationService authenticationService;

    public JwtAuthenticationFilter(TokenService tokenService, AuthenticationService authenticationService) {
        this.tokenService = tokenService;
        this.authenticationService = authenticationService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        bearerToken(request).flatMap(tokenService::verify).ifPresent(userId -> authenticate(userId, request));
        chain.doFilter(request, response);
    }

    private void authenticate(Long userId, HttpServletRequest request) {
        try {
            AppUser user = authenticationService.activeUser(userId);
            AuthenticatedUser principal = AuthenticatedUser.of(user);
            var authentication = new UsernamePasswordAuthenticationToken(principal, null,
                    List.of(new SimpleGrantedAuthority(principal.authority())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (AccessDeniedDomainException e) {
            request.setAttribute(AUTH_ERROR_ATTRIBUTE, e.getMessage());
        }
    }

    /** Токен из заголовка запроса. */
    public static Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER) || header.length() == BEARER.length()) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER.length()).trim());
    }
}
