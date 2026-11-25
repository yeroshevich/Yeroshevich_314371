package by.technosoft.bonus.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.domain.UserStatus;
import by.technosoft.bonus.service.admin.UserAdminService;
import by.technosoft.bonus.service.query.UserQueryService;
import by.technosoft.bonus.web.dto.UserDto;
import by.technosoft.bonus.web.dto.UserRequest;
import by.technosoft.bonus.web.dto.UserStatusRequest;
import by.technosoft.bonus.web.mapper.ApiValues;
import by.technosoft.bonus.web.mapper.UserMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Учётные записи (UC12). */
@RestController
@RequestMapping(ApiPaths.V1 + "/users")
@PreAuthorize(Roles.ADMIN)
@Tag(name = "Пользователи", description = "UC12 «Управление пользователями»")
public class UserController {

    private final UserQueryService userQueryService;
    private final UserAdminService userAdminService;
    private final UserMapper mapper;

    public UserController(UserQueryService userQueryService, UserAdminService userAdminService, UserMapper mapper) {
        this.userQueryService = userQueryService;
        this.userAdminService = userAdminService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Пользователи", description = "С параметром email — проверка дубля: массив из 0 или 1 элемента.")
    public List<UserDto> list(@Parameter(description = "Точный email (без учёта регистра)")
                              @RequestParam(required = false) String email) {
        return userQueryService.list(email).stream().map(mapper::toDto).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать пользователя", description = "Создаёт сотрудника, пустой бонусный счёт и учётную "
            + "запись с временным паролем. 409 EMAIL_EXISTS — email занят.")
    public UserDto create(@AuthenticationPrincipal AuthenticatedUser me, @Valid @RequestBody UserRequest request) {
        return mapper.toDto(userAdminService.create(me.userId(), mapper.toCommand(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить пользователя", description = "ФИО, email, подразделение, должность, роль.")
    public UserDto update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                          @Valid @RequestBody UserRequest request) {
        return mapper.toDto(userAdminService.update(me.userId(), id, mapper.toCommand(request)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Заблокировать / разблокировать", description = "409 — нельзя заблокировать себя.")
    public UserDto setStatus(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                             @Valid @RequestBody UserStatusRequest request) {
        return mapper.toDto(userAdminService.setStatus(me.userId(), id,
                ApiValues.parse(UserStatus.class, request.status())));
    }
}
