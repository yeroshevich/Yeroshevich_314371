package by.technosoft.bonus.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.domain.AppUser;
import by.technosoft.bonus.service.auth.AuthenticationService;
import by.technosoft.bonus.web.dto.AuthSessionDto;
import by.technosoft.bonus.web.dto.LoginRequest;
import by.technosoft.bonus.web.mapper.UserMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import by.technosoft.bonus.web.security.JwtAuthenticationFilter;
import by.technosoft.bonus.web.security.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Аутентификация (SEC-1): вход, профиль по токену, выход. */
@RestController
@RequestMapping(ApiPaths.V1 + "/auth")
@Tag(name = "Аутентификация", description = "SEC-1: вход по email и паролю, JWT HS256 (8 ч)")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final TokenService tokenService;
    private final UserMapper userMapper;

    public AuthController(AuthenticationService authenticationService, TokenService tokenService,
                          UserMapper userMapper) {
        this.authenticationService = authenticationService;
        this.tokenService = tokenService;
        this.userMapper = userMapper;
    }

    @PostMapping("/login")
    @SecurityRequirements
    @Operation(summary = "Вход", description = "Проверка email и пароля (BCrypt). 401 BAD_CREDENTIALS, 403 USER_BLOCKED.")
    public AuthSessionDto login(@Valid @RequestBody LoginRequest request) {
        AppUser user = authenticationService.login(request.email(), request.password());
        return new AuthSessionDto(tokenService.issue(user).value(), userMapper.toDto(user));
    }

    @GetMapping("/me")
    @Operation(summary = "Профиль по токену", description = "Восстановление сессии клиента после перезагрузки вкладки.")
    public AuthSessionDto me(@AuthenticationPrincipal AuthenticatedUser me, HttpServletRequest request) {
        String token = JwtAuthenticationFilter.bearerToken(request).orElseThrow();
        return new AuthSessionDto(token, userMapper.toDto(authenticationService.activeUser(me.userId())));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Выход", description = "Токен без состояния удаляет клиент; сервер фиксирует выход в аудите.")
    public void logout(@AuthenticationPrincipal AuthenticatedUser me) {
        authenticationService.logout(me.userId());
    }
}
