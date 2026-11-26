package by.technosoft.bonus.service.query;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import by.technosoft.bonus.domain.Campaign;
import by.technosoft.bonus.domain.OperationStatus;
import by.technosoft.bonus.repository.AmountByKey;
import by.technosoft.bonus.repository.CampaignRepository;
import by.technosoft.bonus.repository.OperationRepository;
import by.technosoft.bonus.service.campaign.CampaignLifecycleService;
import by.technosoft.bonus.service.exception.ObjectNotFoundException;

/**
 * Чтение бонусных кампаний (UC9) с израсходованным бюджетом.
 * Перед чтением статусы кампаний приводятся к текущей дате ({@link CampaignLifecycleService}, D-1),
 * поэтому {@link #list()} и {@link #get(Long)} выполняются в транзакции на запись.
 */
@Service
@Transactional(readOnly = true)
public class CampaignQueryService {

    /** Израсходовано — проведённые начисления по кампании (accrued, corrected). */
    static final EnumSet<OperationStatus> SPENT_STATUSES = EnumSet.of(OperationStatus.ACCRUED, OperationStatus.CORRECTED);

    /** Кампания (целевые подразделения загружены) и израсходованные баллы. */
    public record CampaignEntry(Campaign campaign, int spent) {
    }

    private final CampaignRepository campaignRepository;
    private final OperationRepository operationRepository;
    private final CampaignLifecycleService campaignLifecycleService;

    public CampaignQueryService(CampaignRepository campaignRepository, OperationRepository operationRepository,
                                CampaignLifecycleService campaignLifecycleService) {
        this.campaignRepository = campaignRepository;
        this.operationRepository = operationRepository;
        this.campaignLifecycleService = campaignLifecycleService;
    }

    /** Все кампании с актуальным статусом, новые сверху. */
    @Transactional
    public List<CampaignEntry> list() {
        campaignLifecycleService.synchronizeToday();
        Map<Long, Long> spent = spentByCampaign();
        return campaignRepository.findAllWithTargets().stream()
                .map(c -> new CampaignEntry(c, spent.getOrDefault(c.getId(), 0L).intValue()))
                .toList();
    }

    /** Кампания с актуальным статусом. */
    @Transactional
    public CampaignEntry get(Long id) {
        campaignLifecycleService.synchronizeToday();
        Campaign campaign = campaignRepository.findWithTargetsById(id)
                .orElseThrow(() -> new ObjectNotFoundException("Кампания не найдена. Обновите список."));
        return new CampaignEntry(campaign, (int) operationRepository.sumAmountByCampaign(id, SPENT_STATUSES));
    }

    Map<Long, Long> spentByCampaign() {
        return operationRepository.sumAmountGroupedByCampaign(SPENT_STATUSES).stream()
                .collect(Collectors.toMap(AmountByKey::key, AmountByKey::amount));
    }
}
