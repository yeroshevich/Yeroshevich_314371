package by.technosoft.bonus.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.admin.RuleAdminService;
import by.technosoft.bonus.service.query.RuleQueryService;
import by.technosoft.bonus.web.dto.RuleDto;
import by.technosoft.bonus.web.dto.RuleRequest;
import by.technosoft.bonus.web.dto.RuleSaveResultDto;
import by.technosoft.bonus.web.mapper.RuleMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Правила начисления (UC8): чтение — запросы, изменение — команды (разные сервисы, SRP). */
@RestController
@RequestMapping(ApiPaths.V1 + "/rules")
@Tag(name = "Правила начисления", description = "UC8 «Настройка правил начисления»")
public class RuleController {

    private final RuleQueryService ruleQueryService;
    private final RuleAdminService ruleAdminService;
    private final RuleMapper mapper;

    public RuleController(RuleQueryService ruleQueryService, RuleAdminService ruleAdminService, RuleMapper mapper) {
        this.ruleQueryService = ruleQueryService;
        this.ruleAdminService = ruleAdminService;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize(Roles.MANAGER_OR_HR)
    @Operation(summary = "Правила", description = "Последние версии правил; HR редактирует, руководитель выбирает в мастере начисления.")
    public List<RuleDto> list() {
        return ruleQueryService.list().stream().map(mapper::toDto).toList();
    }

    @PostMapping
    @PreAuthorize(Roles.HR)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать правило", description = "Действует сразу. 409 RULE_EXISTS — для показателя уже есть правило.")
    public RuleSaveResultDto create(@AuthenticationPrincipal AuthenticatedUser me,
                                    @Valid @RequestBody RuleRequest request) {
        return mapper.toDto(ruleAdminService.create(me.userId(), mapper.toCommand(request)));
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.HR)
    @Operation(summary = "Изменить правило", description = "Правило, применённое в текущем периоде, сохраняется новой "
            + "версией с начала следующего периода (deferred = true).")
    public RuleSaveResultDto update(@AuthenticationPrincipal AuthenticatedUser me, @PathVariable Long id,
                                    @Valid @RequestBody RuleRequest request) {
        return mapper.toDto(ruleAdminService.update(me.userId(), id, mapper.toCommand(request)));
    }
}
