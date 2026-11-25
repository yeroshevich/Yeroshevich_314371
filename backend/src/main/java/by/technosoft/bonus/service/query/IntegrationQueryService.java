package by.technosoft.bonus.service.query;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.Integration;
import by.technosoft.bonus.domain.IntegrationCheck;
import by.technosoft.bonus.repository.IntegrationCheckRepository;
import by.technosoft.bonus.repository.IntegrationRepository;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;

/** Чтение подключений к внешним системам (UC13) с результатом последней проверки соединения. */
@Service
@Transactional(readOnly = true)
public class IntegrationQueryService {

    /** Интеграция и последняя проверка соединения ({@code null} — не проверялась). */
    public record IntegrationState(Integration integration, IntegrationCheck lastCheck) {
    }

    private final IntegrationRepository integrationRepository;
    private final IntegrationCheckRepository checkRepository;

    public IntegrationQueryService(IntegrationRepository integrationRepository,
                                   IntegrationCheckRepository checkRepository) {
        this.integrationRepository = integrationRepository;
        this.checkRepository = checkRepository;
    }

    public List<IntegrationState> list() {
        return integrationRepository.findAll(Sort.by("id")).stream().map(this::state).toList();
    }

    public IntegrationState get(Long id) {
        return integrationRepository.findById(id).map(this::state)
                .orElseThrow(() -> new ObjectNotFoundException("Интеграция не найдена."));
    }

    private IntegrationState state(Integration integration) {
        return new IntegrationState(integration,
                checkRepository.findFirstByIntegrationIdOrderByCheckedAtDescIdDesc(integration.getId()).orElse(null));
    }
}
