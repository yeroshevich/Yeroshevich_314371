package by.technosoft.bonus.web.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import by.technosoft.bonus.service.admin.CampaignAdminService;
import by.technosoft.bonus.service.query.CampaignQueryService;
import by.technosoft.bonus.web.dto.CampaignDto;
import by.technosoft.bonus.web.dto.CampaignRequest;
import by.technosoft.bonus.web.mapper.CampaignMapper;
import by.technosoft.bonus.web.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

/** Бонусные кампании (UC9). */
@RestController
@RequestMapping(ApiPaths.V1 + "/campaigns")
@Tag(name = "Бонусные кампании", description = "UC9 «Управление бонусными кампаниями»")
public class CampaignController {

    private final CampaignQueryService campaignQueryService;
    private final CampaignAdminService campaignAdminService;
    private final CampaignMapper mapper;

    public CampaignController(CampaignQueryService campaignQueryService, CampaignAdminService campaignAdminService,
                              CampaignMapper mapper) {
        this.campaignQueryService = campaignQueryService;
        this.campaignAdminService = campaignAdminService;
        this.mapper = mapper;
    }

    @GetMapping
    @PreAuthorize(Roles.MANAGER_OR_HR)
    @Operation(summary = "Кампании", description = "С израсходованным бюджетом; новые сверху.")
    public List<CampaignDto> list() {
        return campaignQueryService.list().stream().map(mapper::toDto).toList();
    }

    @PostMapping
    @PreAuthorize(Roles.HR)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать кампанию", description = "Без бюджета — статус needs_approval (альт. поток UC9); "
            + "иначе planned / active / finished по датам. 422 — окончание раньше начала.")
    public CampaignDto create(@AuthenticationPrincipal AuthenticatedUser me,
                              @Valid @RequestBody CampaignRequest request) {
        return mapper.toDto(campaignAdminService.create(me.userId(), mapper.toCommand(request)));
    }
}
