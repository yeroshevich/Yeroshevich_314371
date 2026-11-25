package by.technosoft.bonus.web.mapper;

import static by.technosoft.bonus.web.mapper.ApiValues.code;
import static by.technosoft.bonus.web.mapper.ApiValues.id;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import by.technosoft.bonus.domain.Integration;
import by.technosoft.bonus.domain.IntegrationCheck;
import by.technosoft.bonus.domain.IntegrationKind;
import by.technosoft.bonus.service.admin.IntegrationAdminService.IntegrationPatch;
import by.technosoft.bonus.service.query.IntegrationQueryService.IntegrationState;
import by.technosoft.bonus.web.dto.IntegrationCheckDto;
import by.technosoft.bonus.web.dto.IntegrationDto;
import by.technosoft.bonus.web.dto.IntegrationUpdateRequest;

/**
 * Интеграция ↔ JSON (UC13). Ключ API наружу не выдаётся: вместо него — маска {@link #KEY_MASK};
 * маска, пришедшая обратно от клиента, означает «ключ не меняли».
 */
@Component
public class IntegrationMapper {

    public static final String KEY_MASK = "••••••••";

    private static final Map<IntegrationKind, String> DESCRIPTIONS = new EnumMap<>(Map.of(
            IntegrationKind.HRIS, "Кадровые данные: сотрудники, подразделения, должности (DATA-2).",
            IntegrationKind.ERP, "Проекты и этапы для начислений по проектным задачам.",
            IntegrationKind.AD, "Единый вход сотрудников и синхронизация учётных записей.",
            IntegrationKind.NOTIFICATIONS, "Письма и push-уведомления о начислениях и списаниях."));

    public IntegrationDto toDto(IntegrationState state) {
        Integration i = state.integration();
        return new IntegrationDto(id(i.getId()), code(i.getKind()), i.getName(), DESCRIPTIONS.get(i.getKind()),
                i.getUrl(), KEY_MASK, i.isEnabled(), i.getLastSyncAt(),
                state.lastCheck() == null ? null : toDto(state.lastCheck()));
    }

    public IntegrationCheckDto toDto(IntegrationCheck check) {
        return new IntegrationCheckDto(check.isOk(), check.getMessage(), check.getCheckedAt(), check.getLatencyMs());
    }

    public IntegrationPatch toPatch(IntegrationUpdateRequest dto) {
        return new IntegrationPatch(dto.url(), newKeyOrNull(dto.apiKey()), dto.enabled());
    }

    /** Ключ из запроса; маска или пусто — {@code null} («использовать сохранённый»). */
    public String newKeyOrNull(String apiKey) {
        return apiKey == null || apiKey.isBlank() || KEY_MASK.equals(apiKey) ? null : apiKey;
    }
}
