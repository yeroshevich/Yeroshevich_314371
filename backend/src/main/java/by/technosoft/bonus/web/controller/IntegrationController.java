package by.technosoft.bonus.web.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.admin.IntegrationAdminService;
import by.technosoft.bonus.service.query.IntegrationQueryService;
import by.technosoft.bonus.web.dto.IntegrationCheckDto;
import by.technosoft.bonus.web.dto.IntegrationDto;
import by.technosoft.bonus.web.dto.IntegrationTestRequest;
import by.technosoft.bonus.web.dto.IntegrationUpdateRequest;
import by.technosoft.bonus.web.mapper.IntegrationMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Интеграции с внешними системами (UC13). */
@RestController
@RequestMapping(ApiPaths.V1 + "/integrations")
@PreAuthorize(Roles.ADMIN)
@Tag(name = "Интеграции", description = "UC13 «Настройка интеграций»: HRIS, ERP, Active Directory, уведомления")
public class IntegrationController {

    private final IntegrationQueryService integrationQueryService;
    private final IntegrationAdminService integrationAdminService;
    private final IntegrationMapper mapper;

    public IntegrationController(IntegrationQueryService integrationQueryService,
                                 IntegrationAdminService integrationAdminService,
                                 IntegrationMapper mapper) {
        this.integrationQueryService = integrationQueryService;
        this.integrationAdminService = integrationAdminService;
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "Подключения", description = "С результатом последней проверки; ключ API — маской.")
    public List<IntegrationDto> list() {
        return integrationQueryService.list().stream().map(mapper::toDto).toList();
    }

    @PostMapping("/{id}/test")
    @Operation(summary = "Проверить соединение", description = "Проверка с переданными адресом и ключом; результат "
            + "(ok = false — тоже 200) сохраняется в журнал проверок.")
    public IntegrationCheckDto test(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                                    @Valid @RequestBody IntegrationTestRequest request) {
        return mapper.toDto(integrationAdminService.test(me.userId(), id, request.url(),
                mapper.newKeyOrNull(request.apiKey())));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Изменить подключение", description = "Адрес и ключ сохраняются только при успешной проверке "
            + "соединения (иначе 409); enabled = true — только после успешной проверки (409).")
    public IntegrationDto update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                                 @Valid @RequestBody IntegrationUpdateRequest request) {
        return mapper.toDto(integrationAdminService.update(me.userId(), id, mapper.toPatch(request)));
    }
}
